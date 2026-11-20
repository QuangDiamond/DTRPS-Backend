package com.dtrps.modules.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.SystemSettingsService;
import com.dtrps.modules.advertisement.entity.Advertisement;
import com.dtrps.modules.advertisement.repository.AdvertisementRepository;
import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.businessowner.entity.BusinessProfile;
import com.dtrps.modules.businessowner.repository.BusinessListingRepository;
import com.dtrps.modules.businessowner.repository.BusinessProfileRepository;
import com.dtrps.modules.payment.config.PayOsProperties;
import com.dtrps.modules.payment.dto.AdDtos;
import com.dtrps.modules.payment.entity.AdPackage;
import com.dtrps.modules.payment.entity.Payment;
import com.dtrps.modules.payment.entity.Subscription;
import com.dtrps.modules.payment.entity.SubscriptionRenewalWindow;
import com.dtrps.modules.payment.repository.AdPackageRepository;
import com.dtrps.modules.payment.repository.PaymentIssueRepository;
import com.dtrps.modules.payment.repository.PaymentRepository;
import com.dtrps.modules.payment.repository.SubscriptionRenewalWindowRepository;
import com.dtrps.modules.payment.repository.SubscriptionRepository;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.user.entity.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

/** Unit test PartnerAdService — sheet Payment, UTC001–UTC025 trong Report5.1 Unit Test. */
@ExtendWith(MockitoExtension.class)
class PartnerAdServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Long PARTNER_ID = 7L;
  private static final Long BUSINESS_ID = 3L;
  private static final long ORDER = 100_010L;
  private static final String RESULT_URL = "http://localhost:5173/partner/ads/payment-result";
  private static final String CHECKOUT_URL = "https://pay.payos.vn/web/abc";

  @Mock private AdPackageRepository packages;
  @Mock private SubscriptionRepository subscriptions;
  @Mock private SubscriptionRenewalWindowRepository windows;
  @Mock private PaymentRepository payments;
  @Mock private PaymentIssueRepository issues;
  @Mock private AdvertisementRepository advertisements;
  @Mock private BusinessProfileRepository profiles;
  @Mock private BusinessListingRepository listings;
  @Mock private PlaceImageRepository images;
  @Mock private PayOsGateway gateway;
  @Mock private AdPaymentSyncService sync;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private SystemSettingsService settings;

  private PartnerAdService service;
  private User partner;
  private BusinessProfile business;
  private AdPackage basic;

  /** Đơn vừa được lưu qua payments.saveAndFlush */
  private final List<Payment> saved = new ArrayList<>();

  @BeforeEach
  void setUp() {
    PayOsProperties properties =
        new PayOsProperties(
            "client",
            "key",
            "checksum",
            "https://api-merchant.payos.vn",
            "http://localhost:5173/",
            Duration.ofMinutes(15),
            false);
    service =
        new PartnerAdService(
            packages,
            subscriptions,
            windows,
            payments,
            issues,
            advertisements,
            profiles,
            listings,
            images,
            gateway,
            sync,
            properties,
            transactionManager,
            settings,
            Clock.fixed(NOW, ZoneOffset.UTC));

    partner = new User();
    partner.setUserId(PARTNER_ID);
    business = new BusinessProfile();
    business.setBusinessId(BUSINESS_ID);
    business.setUser(partner);
    business.setStatus("ACTIVE");
    lenient().when(profiles.findByUserUserId(PARTNER_ID)).thenReturn(Optional.of(business));

    basic = new AdPackage();
    basic.setPackageId(1L);
    basic.setName("Gói cơ bản");
    basic.setPrice(new BigDecimal("100000"));
    basic.setDurationDays(30);
    basic.setMaxPlaces(3);
    lenient().when(packages.findById(1L)).thenReturn(Optional.of(basic));

    lenient().when(settings.paymentLinkTtl()).thenReturn(Duration.ofMinutes(15));
    lenient().when(settings.renewalNotice()).thenReturn(Duration.ofDays(7));
    lenient().when(payments.nextOrderCode()).thenReturn(ORDER);
    lenient()
        .when(subscriptions.saveAndFlush(any(Subscription.class)))
        .thenAnswer(call -> call.getArgument(0));
    lenient()
        .when(payments.saveAndFlush(any(Payment.class)))
        .thenAnswer(
            call -> {
              Payment payment = call.getArgument(0);
              payment.setPaymentId(50L);
              saved.add(payment);
              return payment;
            });
    lenient()
        .when(payments.findById(50L))
        .thenAnswer(call -> Optional.of(saved.get(saved.size() - 1)));
  }

  private BusinessListing listing(long id, String listingStatus, String placeStatus) {
    Place place = new Place();
    place.setPlaceId(100 + id);
    place.setName("Địa điểm " + id);
    place.setStatus(placeStatus);
    BusinessListing listing = new BusinessListing();
    listing.setListingId(id);
    listing.setStatus(listingStatus);
    listing.setPlace(place);
    listing.setBusiness(business);
    return listing;
  }

  private Subscription activeSubscription() {
    Subscription subscription = new Subscription();
    subscription.setSubscriptionId(20L);
    subscription.setBusiness(business);
    subscription.setAdPackage(basic);
    subscription.setPackageName("Gói cơ bản");
    subscription.setMaxPlaces(3);
    subscription.setDurationDays(30);
    subscription.setMonthlyFee(new BigDecimal("100000"));
    subscription.setStatus("ACTIVE");
    subscription.setStartDate(NOW.minus(Duration.ofDays(25)));
    subscription.setEndDate(NOW.plus(Duration.ofDays(5)));
    return subscription;
  }

  private Payment ownedPayment(String status, User owner) {
    BusinessProfile ownerBusiness = new BusinessProfile();
    ownerBusiness.setUser(owner);
    Subscription subscription = new Subscription();
    subscription.setBusiness(ownerBusiness);
    subscription.setPackageName("Gói cơ bản");
    Payment payment = new Payment();
    payment.setPaymentId(60L);
    payment.setSubscription(subscription);
    payment.setTransactionReference(String.valueOf(ORDER));
    payment.setAmount(new BigDecimal("100000"));
    payment.setStatus(status);
    lenient()
        .when(payments.findByPaymentGatewayAndTransactionReference("PAYOS", String.valueOf(ORDER)))
        .thenReturn(Optional.of(payment));
    return payment;
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  @Nested
  @DisplayName("checkout")
  class Checkout {
    @BeforeEach
    void eligibleListing() {
      lenient()
          .when(listings.findByBusinessUserUserIdOrderByListingIdDesc(PARTNER_ID))
          .thenReturn(List.of(listing(1, "APPROVED", "ACTIVE")));
    }

    @Test
    @DisplayName("UTC001 Hồ sơ ACTIVE, có địa điểm duyệt: tạo đơn, giá lấy từ DB, trả link PayOS")
    void createsPaymentLink() {
      when(gateway.createLink(
              eq(ORDER), eq(100_000L), eq("ST" + ORDER), eq(RESULT_URL), eq(RESULT_URL), anyLong()))
          .thenReturn(new PayOsGateway.PaymentLink("pl-1", CHECKOUT_URL));

      AdDtos.CheckoutResponse response = service.checkout(PARTNER_ID, 1L);

      assertThat(response.orderCode()).isEqualTo(String.valueOf(ORDER));
      assertThat(response.checkoutUrl()).isEqualTo(CHECKOUT_URL);
      Payment payment = saved.get(0);
      assertThat(payment.getAmount()).isEqualByComparingTo("100000");
      assertThat(payment.getPaymentType()).isEqualTo("INITIAL");
      assertThat(payment.getLinkExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
      assertThat(payment.getCheckoutUrl()).isEqualTo(CHECKOUT_URL);
      assertThat(payment.getPaymentLinkId()).isEqualTo("pl-1");
      assertThat(payment.getSubscription().getStatus()).isEqualTo("PENDING");
      assertThat(payment.getSubscription().getMaxPlaces()).isEqualTo(3);
    }

    @Test
    @DisplayName("UTC002 Chưa có hồ sơ đối tác → 404")
    void noProfile_notFound() {
      when(profiles.findByUserUserId(PARTNER_ID)).thenReturn(Optional.empty());

      ApiException e = error(() -> service.checkout(PARTNER_ID, 1L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Bạn chưa có hồ sơ đối tác");
    }

    @Test
    @DisplayName("UTC003 Hồ sơ bị khoá → 403")
    void suspendedProfile_forbidden() {
      business.setStatus("SUSPENDED");

      ApiException e = error(() -> service.checkout(PARTNER_ID, 1L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(e.getMessage()).isEqualTo("Hồ sơ đối tác chưa hoạt động");
    }

    @Test
    @DisplayName("UTC004 Gói đã tắt → 404")
    void inactivePackage_notFound() {
      basic.setStatus("INACTIVE");

      ApiException e = error(() -> service.checkout(PARTNER_ID, 1L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Gói không còn được bán");
    }

    @Test
    @DisplayName("UTC005 Đang có gói ACTIVE → 409")
    void activeSubscription_conflict() {
      when(subscriptions.existsByBusinessBusinessIdAndStatus(BUSINESS_ID, "ACTIVE"))
          .thenReturn(true);

      ApiException e = error(() -> service.checkout(PARTNER_ID, 1L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Bạn đang có gói chạy, chỉ có thể gia hạn");
    }

    @Test
    @DisplayName("UTC006 Chưa có địa điểm nào đủ điều kiện → 409")
    void noEligibleListing_conflict() {
      when(listings.findByBusinessUserUserIdOrderByListingIdDesc(PARTNER_ID))
          .thenReturn(List.of(listing(1, "PENDING", "DRAFT"), listing(2, "APPROVED", "PAUSED")));

      ApiException e = error(() -> service.checkout(PARTNER_ID, 1L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage())
          .isEqualTo("Bạn cần có ít nhất 1 địa điểm đã được duyệt trước khi mua gói");
    }

    @Test
    @DisplayName("UTC007 Bấm mua lại khi đơn cùng gói còn hạn: trả đúng link cũ, không tạo đơn")
    void pendingSamePackage_reusesLink() {
      Payment pending = ownedPayment("PENDING", partner);
      pending.getSubscription().setAdPackage(basic);
      pending.setCheckoutUrl(CHECKOUT_URL);
      pending.setLinkExpiresAt(NOW.plus(Duration.ofMinutes(10)));
      when(payments.findBySubscriptionBusinessBusinessIdAndStatusOrderByPaymentIdDesc(
              BUSINESS_ID, "PENDING"))
          .thenReturn(List.of(pending));

      AdDtos.CheckoutResponse response = service.checkout(PARTNER_ID, 1L);

      assertThat(response.checkoutUrl()).isEqualTo(CHECKOUT_URL);
      verify(payments, never()).saveAndFlush(any());
      verify(gateway, never())
          .createLink(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("UTC008 Đang có đơn chờ cho gói khác → 409")
    void pendingOtherPackage_conflict() {
      AdPackage other = new AdPackage();
      other.setPackageId(2L);
      Payment pending = ownedPayment("PENDING", partner);
      pending.getSubscription().setAdPackage(other);
      pending.setCheckoutUrl(CHECKOUT_URL);
      pending.setLinkExpiresAt(NOW.plus(Duration.ofMinutes(10)));
      when(payments.findBySubscriptionBusinessBusinessIdAndStatusOrderByPaymentIdDesc(
              BUSINESS_ID, "PENDING"))
          .thenReturn(List.of(pending));

      ApiException e = error(() -> service.checkout(PARTNER_ID, 1L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage())
          .isEqualTo("Bạn đang có đơn chờ thanh toán, hãy huỷ đơn đó trước khi đổi gói");
    }

    @Test
    @DisplayName("UTC009 PayOS lỗi khi tạo link: đơn FAILED, đăng ký CANCELLED, báo 502")
    void gatewayError_marksFailed() {
      when(gateway.createLink(
              anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong()))
          .thenThrow(
              new ApiException(
                  HttpStatus.BAD_GATEWAY, "Cổng thanh toán đang lỗi, vui lòng thử lại"));

      ApiException e = error(() -> service.checkout(PARTNER_ID, 1L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
      assertThat(e.getMessage()).isEqualTo("Cổng thanh toán đang lỗi, vui lòng thử lại");
      Payment payment = saved.get(0);
      assertThat(payment.getStatus()).isEqualTo("FAILED");
      assertThat(payment.getSubscription().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Bấm mua 2 lần cùng lúc (vướng unique index) → 409")
    void concurrentCheckout_conflict() {
      when(subscriptions.saveAndFlush(any(Subscription.class)))
          .thenThrow(new DataIntegrityViolationException("uq_business_pending_subscription"));

      ApiException e = error(() -> service.checkout(PARTNER_ID, 1L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Đơn đang được tạo, vui lòng thử lại sau giây lát");
    }
  }

  @Nested
  @DisplayName("renew")
  class Renew {
    @Test
    @DisplayName("UTC010 Cửa sổ gia hạn OPEN: tạo đơn RENEWAL giữ giá lúc mua")
    void openWindow_createsRenewal() {
      Subscription subscription = activeSubscription();
      subscription.setMonthlyFee(new BigDecimal("80000")); // giá lúc mua, bảng giá giờ đã 100k
      SubscriptionRenewalWindow window = new SubscriptionRenewalWindow();
      window.setStatus("OPEN");
      when(subscriptions.lockActiveByBusiness(BUSINESS_ID)).thenReturn(Optional.of(subscription));
      when(windows.findFirstBySubscriptionSubscriptionIdAndStatus(20L, "OPEN"))
          .thenReturn(Optional.of(window));
      when(gateway.createLink(
              anyLong(), eq(80_000L), anyString(), anyString(), anyString(), anyLong()))
          .thenReturn(new PayOsGateway.PaymentLink("pl-2", CHECKOUT_URL));

      AdDtos.CheckoutResponse response = service.renew(PARTNER_ID);

      assertThat(response.checkoutUrl()).isEqualTo(CHECKOUT_URL);
      Payment payment = saved.get(0);
      assertThat(payment.getPaymentType()).isEqualTo("RENEWAL");
      assertThat(payment.getAmount()).isEqualByComparingTo("80000");
      assertThat(payment.getRenewalWindow()).isSameAs(window);
    }

    @Test
    @DisplayName("UTC011 Chưa có gói đang chạy → 409")
    void noActiveSubscription_conflict() {
      ApiException e = error(() -> service.renew(PARTNER_ID));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Bạn chưa có gói đang chạy");
    }

    @Test
    @DisplayName("UTC012 Chưa mở cửa sổ gia hạn → 409")
    void windowNotOpen_conflict() {
      when(subscriptions.lockActiveByBusiness(BUSINESS_ID))
          .thenReturn(Optional.of(activeSubscription()));

      ApiException e = error(() -> service.renew(PARTNER_ID));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Chỉ gia hạn được trong 7 ngày cuối của gói");
      verify(payments, never()).saveAndFlush(any());
    }
  }

  @Nested
  @DisplayName("cancelPayment")
  class CancelPayment {
    @Test
    @DisplayName("UTC013 Đơn PENDING của mình: huỷ trên PayOS và hệ thống")
    void pending_cancels() {
      ownedPayment("PENDING", partner);

      service.cancelPayment(PARTNER_ID, ORDER);

      verify(sync).cancelByPartner(ORDER);
    }

    @Test
    @DisplayName("UTC014 Đơn đã SUCCESS → 409")
    void notPending_conflict() {
      ownedPayment("SUCCESS", partner);

      ApiException e = error(() -> service.cancelPayment(PARTNER_ID, ORDER));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Đơn này không còn chờ thanh toán");
      verify(sync, never()).cancelByPartner(anyLong());
    }

    @Test
    @DisplayName("UTC015 Đơn của đối tác khác → 404")
    void otherOwner_notFound() {
      User other = new User();
      other.setUserId(8L);
      ownedPayment("PENDING", other);

      ApiException e = error(() -> service.cancelPayment(PARTNER_ID, ORDER));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy đơn thanh toán");
    }
  }

  @Nested
  @DisplayName("checkPayment")
  class CheckPayment {
    @Test
    @DisplayName("UTC016 Đơn đã huỷ trên hệ thống: vẫn hỏi lại PayOS")
    void cancelledOrder_reconciles() {
      ownedPayment("CANCELLED", partner);

      service.checkPayment(PARTNER_ID, ORDER);

      verify(sync).reconcile(ORDER);
    }

    @Test
    @DisplayName("UTC017 Đơn PENDING: hỏi lại PayOS, trả trạng thái mới nhất")
    void pendingOrder_reconciles() {
      ownedPayment("PENDING", partner);

      AdDtos.PaymentItem item = service.checkPayment(PARTNER_ID, ORDER);

      verify(sync).reconcile(ORDER);
      assertThat(item.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("UTC018 Không có đơn → 404")
    void unknownOrder_notFound() {
      ApiException e = error(() -> service.checkPayment(PARTNER_ID, 1L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy đơn thanh toán");
    }

    @Test
    @DisplayName("Đơn đã SUCCESS: không gọi PayOS nữa")
    void paidOrder_skipsGateway() {
      ownedPayment("SUCCESS", partner);

      service.checkPayment(PARTNER_ID, ORDER);

      verify(sync, never()).reconcile(anyLong());
    }
  }

  @Nested
  @DisplayName("saveSlots")
  class SaveSlots {
    private Subscription subscription;

    @BeforeEach
    void activeWithListings() {
      subscription = activeSubscription();
      lenient()
          .when(subscriptions.lockActiveByBusiness(BUSINESS_ID))
          .thenReturn(Optional.of(subscription));
      lenient()
          .when(listings.findByBusinessUserUserIdOrderByListingIdDesc(PARTNER_ID))
          .thenReturn(
              List.of(
                  listing(1, "APPROVED", "ACTIVE"),
                  listing(2, "APPROVED", "ACTIVE"),
                  listing(3, "APPROVED", "ACTIVE"),
                  listing(4, "APPROVED", "ACTIVE"),
                  listing(9, "APPROVED", "PAUSED")));
    }

    private List<Advertisement> savedAds(int times) {
      ArgumentCaptor<Advertisement> captor = ArgumentCaptor.forClass(Advertisement.class);
      verify(advertisements, times(times)).save(captor.capture());
      return captor.getAllValues();
    }

    @Test
    @DisplayName("UTC019 Gói 3 lượt, chọn 2: tạo 2 quảng cáo PUBLISHED tới ngày hết hạn gói")
    void twoOfThree_published() {
      service.saveSlots(PARTNER_ID, List.of(1L, 2L));

      List<Advertisement> ads = savedAds(2);
      assertThat(ads).extracting(Advertisement::getStatus).containsOnly("PUBLISHED");
      assertThat(ads).extracting(Advertisement::getEndAt).containsOnly(subscription.getEndDate());
      assertThat(ads).extracting(Advertisement::getStartAt).containsOnly(NOW);
    }

    @Test
    @DisplayName("UTC020 Chọn đúng bằng số lượt (3/3) → hợp lệ")
    void exactlyMax_published() {
      service.saveSlots(PARTNER_ID, List.of(1L, 2L, 3L));

      assertThat(savedAds(3)).hasSize(3);
    }

    @Test
    @DisplayName("UTC021 Chọn 4 khi gói 3 lượt → 400")
    void overMax_badRequest() {
      ApiException e = error(() -> service.saveSlots(PARTNER_ID, List.of(1L, 2L, 3L, 4L)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Gói của bạn chỉ quảng cáo được tối đa 3 địa điểm");
      verify(advertisements, never()).save(any());
    }

    @Test
    @DisplayName("UTC022 Danh sách trùng → 400")
    void duplicate_badRequest() {
      ApiException e = error(() -> service.saveSlots(PARTNER_ID, List.of(1L, 1L)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Danh sách địa điểm bị trùng");
    }

    @Test
    @DisplayName("UTC023 Địa điểm đang tạm ngưng → 409 kèm tên và lý do")
    void ineligible_conflict() {
      ApiException e = error(() -> service.saveSlots(PARTNER_ID, List.of(9L)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("\"Địa điểm 9\": Địa điểm đang tạm ngưng");
    }

    @Test
    @DisplayName("UTC024 Chưa có gói đang chạy → 409")
    void noSubscription_conflict() {
      when(subscriptions.lockActiveByBusiness(BUSINESS_ID)).thenReturn(Optional.empty());

      ApiException e = error(() -> service.saveSlots(PARTNER_ID, List.of(1L)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Bạn chưa có gói đang chạy");
    }

    @Test
    @DisplayName("UTC025 Quảng cáo bị admin gỡ vẫn SUSPENDED; địa điểm bỏ chọn thì EXPIRED")
    void suspendedKept_deselectedExpired() {
      Advertisement suspended = new Advertisement();
      suspended.setListing(listing(1, "APPROVED", "ACTIVE"));
      suspended.setStatus("SUSPENDED");
      Advertisement dropped = new Advertisement();
      dropped.setListing(listing(3, "APPROVED", "ACTIVE"));
      dropped.setStatus("PUBLISHED");
      when(advertisements.findBySubscriptionSubscriptionIdAndStatusIn(
              20L, AdSubscriptionOps.SLOT_STATUSES))
          .thenReturn(List.of(suspended, dropped));

      service.saveSlots(PARTNER_ID, List.of(1L, 2L));

      assertThat(suspended.getStatus()).isEqualTo("SUSPENDED");
      assertThat(dropped.getStatus()).isEqualTo("EXPIRED");
      assertThat(dropped.getEndAt()).isEqualTo(NOW);
      assertThat(savedAds(1).get(0).getListing().getListingId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Địa điểm không thuộc đối tác → 404")
    void notMine_notFound() {
      ApiException e = error(() -> service.saveSlots(PARTNER_ID, List.of(77L)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy địa điểm");
    }
  }
}
