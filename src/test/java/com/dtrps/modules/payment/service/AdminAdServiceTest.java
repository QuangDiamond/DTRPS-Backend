package com.dtrps.modules.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.advertisement.entity.Advertisement;
import com.dtrps.modules.advertisement.repository.AdvertisementRepository;
import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.businessowner.entity.BusinessProfile;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.payment.dto.AdDtos;
import com.dtrps.modules.payment.entity.AdPackage;
import com.dtrps.modules.payment.entity.Payment;
import com.dtrps.modules.payment.entity.Subscription;
import com.dtrps.modules.payment.repository.AdPackageRepository;
import com.dtrps.modules.payment.repository.PaymentIssueRepository;
import com.dtrps.modules.payment.repository.PaymentRepository;
import com.dtrps.modules.payment.repository.SubscriptionRepository;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Unit test AdminAdService — sheet Payment, UTC040–UTC057 trong Report5.1 Unit Test. */
@ExtendWith(MockitoExtension.class)
class AdminAdServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Long ADMIN_ID = 1L;
  private static final Long PARTNER_ID = 7L;
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Mock private AdPackageRepository packages;
  @Mock private PaymentRepository payments;
  @Mock private PaymentIssueRepository issues;
  @Mock private SubscriptionRepository subscriptions;
  @Mock private AdvertisementRepository advertisements;
  @Mock private UserRepository users;
  @Mock private AdSubscriptionOps subscriptionOps;
  @Mock private AuditService auditService;
  @Mock private NotificationService notifications;

  private AdminAdService service;
  private AdPackage basic;
  private User partner;
  private BusinessProfile business;

  @BeforeEach
  void setUp() {
    service =
        new AdminAdService(
            packages,
            payments,
            issues,
            subscriptions,
            advertisements,
            users,
            subscriptionOps,
            auditService,
            notifications,
            Clock.fixed(NOW, ZoneOffset.UTC));
    basic = new AdPackage();
    basic.setPackageId(1L);
    basic.setName("Gói cơ bản");
    basic.setPrice(new BigDecimal("100000"));
    basic.setDurationDays(30);
    basic.setMaxPlaces(3);
    basic.setSortOrder(1);
    lenient().when(packages.findAll()).thenReturn(List.of(basic));
    lenient().when(packages.findById(1L)).thenReturn(Optional.of(basic));

    partner = new User();
    partner.setUserId(PARTNER_ID);
    business = new BusinessProfile();
    business.setBusinessId(3L);
    business.setBusinessName("Cty Du lịch A");
    business.setUser(partner);
  }

  private static AdDtos.PackageRequest request(
      String name, String price, String days, String places, String order) {
    return new AdDtos.PackageRequest(
        name,
        null,
        new BigDecimal(price),
        new BigDecimal(days),
        new BigDecimal(places),
        new BigDecimal(order));
  }

  private static Set<String> messages(Object request) {
    return VALIDATOR.validate(request).stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  private Subscription subscription(String status) {
    Subscription subscription = new Subscription();
    subscription.setSubscriptionId(20L);
    subscription.setBusiness(business);
    subscription.setPackageName("Gói cơ bản");
    subscription.setStatus(status);
    subscription.setEndDate(NOW.plus(Duration.ofDays(5)));
    return subscription;
  }

  @Nested
  @DisplayName("createPackage")
  class CreatePackage {
    @Test
    @DisplayName("UTC040 Dữ liệu hợp lệ: tạo gói ACTIVE, ghi audit")
    void valid_creates() {
      AdDtos.PackageRequest request = request("Gói 5 địa điểm", "150000", "30", "5", "2");
      assertThat(messages(request)).isEmpty();

      AdDtos.PackageItem item = service.createPackage(ADMIN_ID, request);

      ArgumentCaptor<AdPackage> captor = ArgumentCaptor.forClass(AdPackage.class);
      verify(packages).save(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
      assertThat(captor.getValue().getCode()).startsWith("PKG_");
      assertThat(item.maxPlaces()).isEqualTo(5);
      verify(auditService).record(eq(ADMIN_ID), eq("AD_PACKAGE_CREATED"), eq("ad_packages"), any());
    }

    @Test
    @DisplayName("UTC041 Tên trùng gói khác (không phân biệt hoa thường) → 409")
    void duplicateName_conflict() {
      ApiException e =
          error(
              () ->
                  service.createPackage(ADMIN_ID, request("gói CƠ BẢN", "150000", "30", "5", "2")));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Tên gói này đã tồn tại");
      verify(packages, never()).save(any());
    }

    @Test
    @DisplayName("UTC042 Thứ tự đã dùng cho gói khác → 409")
    void duplicateOrder_conflict() {
      ApiException e =
          error(
              () -> service.createPackage(ADMIN_ID, request("Gói mới", "150000", "30", "5", "1")));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Thứ tự 1 đang được dùng cho gói \"Gói cơ bản\"");
    }

    @Test
    @DisplayName("UTC043 Giá 999đ → 400 (validation)")
    void priceBelowMin_invalid() {
      assertThat(messages(request("Gói mới", "999", "30", "5", "2")))
          .containsExactly("Giá tối thiểu 1.000đ");
    }

    @Test
    @DisplayName("UTC044 Giá đúng 1.000đ → hợp lệ")
    void priceAtMin_valid() {
      AdDtos.PackageRequest request = request("Gói mới", "1000", "30", "5", "2");
      assertThat(messages(request)).isEmpty();

      AdDtos.PackageItem item = service.createPackage(ADMIN_ID, request);

      assertThat(item.price()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("UTC045 Thứ tự 1.1 → 400 (validation)")
    void decimalOrder_invalid() {
      assertThat(messages(request("Gói mới", "150000", "30", "5", "1.1")))
          .containsExactly("Thứ tự phải là số nguyên");
    }

    @Test
    @DisplayName("UTC046 Số địa điểm 101 → 400 (validation)")
    void placesOverMax_invalid() {
      assertThat(messages(request("Gói mới", "150000", "30", "101", "2")))
          .containsExactly("Số địa điểm phải từ 1 đến 100");
    }
  }

  @Nested
  @DisplayName("updatePackage")
  class UpdatePackage {
    @Test
    @DisplayName("UTC047 Đổi giá: lưu giá mới (đăng ký cũ giữ giá lúc mua)")
    void changePrice_saves() {
      AdDtos.PackageItem item =
          service.updatePackage(ADMIN_ID, 1L, request("Gói cơ bản", "120000", "30", "3", "1"));

      assertThat(basic.getPrice()).isEqualByComparingTo("120000");
      assertThat(item.price()).isEqualByComparingTo("120000");
      verify(subscriptions, never()).save(any());
    }

    @Test
    @DisplayName("UTC048 Giữ nguyên tên và thứ tự của chính nó → không báo trùng")
    void sameAsItself_ok() {
      AdDtos.PackageItem item =
          service.updatePackage(ADMIN_ID, 1L, request("Gói cơ bản", "100000", "30", "3", "1"));

      assertThat(item.name()).isEqualTo("Gói cơ bản");
      verify(auditService).record(ADMIN_ID, "AD_PACKAGE_UPDATED", "ad_packages", 1L);
    }

    @Test
    @DisplayName("UTC049 Gói không tồn tại → 404")
    void missing_notFound() {
      ApiException e =
          error(
              () -> service.updatePackage(ADMIN_ID, 999L, request("X", "100000", "30", "3", "5")));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy gói");
    }
  }

  @Nested
  @DisplayName("refund")
  class Refund {
    private Payment payment;
    private Subscription subscription;

    @BeforeEach
    void successPayment() {
      subscription = subscription("ACTIVE");
      payment = new Payment();
      payment.setPaymentId(50L);
      payment.setUser(partner);
      payment.setSubscription(subscription);
      payment.setTransactionReference("100010");
      payment.setAmount(new BigDecimal("100000"));
      payment.setStatus("SUCCESS");
      lenient().when(payments.findWithDetailsByPaymentId(50L)).thenReturn(Optional.of(payment));
      lenient().when(users.getReferenceById(ADMIN_ID)).thenReturn(new User());
    }

    @Test
    @DisplayName("UTC050 Giao dịch SUCCESS, gói đang chạy: REFUNDED, dừng gói, báo đối tác")
    void success_refundsAndEndsSubscription() {
      service.refund(ADMIN_ID, 50L, " Trả trùng ", " FT999 ");

      assertThat(payment.getStatus()).isEqualTo("REFUNDED");
      assertThat(payment.getRefundReason()).isEqualTo("Trả trùng");
      assertThat(payment.getRefundReference()).isEqualTo("FT999");
      assertThat(payment.getRefundedAt()).isEqualTo(NOW);
      verify(subscriptionOps).end(subscription, "CANCELLED", "Hoàn tiền: Trả trùng");
      ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
      verify(notifications).notifyUser(eq(PARTNER_ID), captor.capture());
      assertThat(captor.getValue().type()).isEqualTo("AD_PAYMENT_REFUNDED");
    }

    @Test
    @DisplayName("UTC051 Giao dịch PENDING → 409")
    void notSuccess_conflict() {
      payment.setStatus("PENDING");

      ApiException e = error(() -> service.refund(ADMIN_ID, 50L, "x", "FT1"));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Chỉ hoàn tiền được giao dịch đã thành công");
    }

    @Test
    @DisplayName("UTC052 Giao dịch cần kiểm tra (chưa kích hoạt dịch vụ): không đụng gói đang chạy")
    void needsReview_keepsSubscription() {
      payment.setNeedsReview(true);

      service.refund(ADMIN_ID, 50L, "Trả muộn", "FT999");

      assertThat(payment.getStatus()).isEqualTo("REFUNDED");
      assertThat(payment.isNeedsReview()).isFalse();
      verify(subscriptionOps, never()).end(any(), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("cancelSubscription")
  class CancelSubscription {
    @Test
    @DisplayName("UTC053 Gói ACTIVE: huỷ, dừng quảng cáo, báo đối tác")
    void active_cancels() {
      Subscription subscription = subscription("ACTIVE");
      when(subscriptions.lockById(20L)).thenReturn(Optional.of(subscription));

      service.cancelSubscription(ADMIN_ID, 20L, " Vi phạm ");

      verify(subscriptionOps).end(subscription, "CANCELLED", "Vi phạm");
      verify(auditService)
          .record(ADMIN_ID, "AD_SUBSCRIPTION_CANCELLED", "subscriptions", 20L, "Vi phạm");
      verify(notifications).notifyUser(eq(PARTNER_ID), any(Notice.class));
    }

    @Test
    @DisplayName("UTC054 Gói đã EXPIRED → 409")
    void expired_conflict() {
      when(subscriptions.lockById(20L)).thenReturn(Optional.of(subscription("EXPIRED")));

      ApiException e = error(() -> service.cancelSubscription(ADMIN_ID, 20L, "x"));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Gói này không còn hoạt động");
      verify(subscriptionOps, never()).end(any(), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("changeAdStatus")
  class ChangeAdStatus {
    private Advertisement ad;

    @BeforeEach
    void publishedAd() {
      Place place = new Place();
      place.setPlaceId(101L);
      place.setName("Homestay A");
      BusinessListing listing = new BusinessListing();
      listing.setListingId(1L);
      listing.setPlace(place);
      listing.setBusiness(business);
      ad = new Advertisement();
      ad.setAdvertisementId(30L);
      ad.setListing(listing);
      ad.setSubscription(subscription("ACTIVE"));
      ad.setStatus("PUBLISHED");
      lenient()
          .when(advertisements.findWithDetailsByAdvertisementId(30L))
          .thenReturn(Optional.of(ad));
    }

    @Test
    @DisplayName("UTC055 Gỡ quảng cáo PUBLISHED: SUSPENDED kèm lý do, báo đối tác")
    void suspend_published() {
      AdDtos.AdminAdvertisement result =
          service.changeAdStatus(ADMIN_ID, 30L, "SUSPENDED", " Ảnh sai ");

      assertThat(result.status()).isEqualTo("SUSPENDED");
      assertThat(ad.getSuspendReason()).isEqualTo("Ảnh sai");
      ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
      verify(notifications).notifyUser(eq(PARTNER_ID), captor.capture());
      assertThat(captor.getValue().type()).isEqualTo("AD_SUSPENDED");
      assertThat(captor.getValue().message()).isEqualTo("\"Homestay A\". Lý do: Ảnh sai");
    }

    @Test
    @DisplayName("UTC056 Bật lại quảng cáo đã EXPIRED → 409")
    void restoreExpired_conflict() {
      ad.setStatus("EXPIRED");

      ApiException e = error(() -> service.changeAdStatus(ADMIN_ID, 30L, "PUBLISHED", "x"));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Quảng cáo không ở trạng thái phù hợp");
    }

    @Test
    @DisplayName("UTC057 Bật lại khi gói đã hết hạn → 409")
    void restoreWithEndedSubscription_conflict() {
      ad.setStatus("SUSPENDED");
      ad.getSubscription().setStatus("EXPIRED");

      ApiException e = error(() -> service.changeAdStatus(ADMIN_ID, 30L, "PUBLISHED", "x"));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Gói của quảng cáo này không còn hoạt động");
      assertThat(ad.getStatus()).isEqualTo("SUSPENDED");
    }
  }
}
