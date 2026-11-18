package com.dtrps.modules.payment.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
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
import com.dtrps.modules.payment.repository.PaymentRepository;
import com.dtrps.modules.payment.repository.SubscriptionRenewalWindowRepository;
import com.dtrps.modules.payment.repository.SubscriptionRepository;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Đối tác mua / gia hạn gói quảng cáo và chọn địa điểm được quảng cáo. */
@Service
public class PartnerAdService {
  private static final String ACTIVE = "ACTIVE";
  private static final String PENDING = "PENDING";
  private static final String PUBLISHED = "PUBLISHED";
  private static final String SUSPENDED = "SUSPENDED";
  private static final String APPROVED = "APPROVED";
  private static final String RESULT_PATH = "/partner/ads/payment-result";
  private static final Duration DEFAULT_LINK_TTL = Duration.ofMinutes(15);

  private final AdPackageRepository packages;
  private final SubscriptionRepository subscriptions;
  private final SubscriptionRenewalWindowRepository windows;
  private final PaymentRepository payments;
  private final AdvertisementRepository advertisements;
  private final BusinessProfileRepository profiles;
  private final BusinessListingRepository listings;
  private final PlaceImageRepository images;
  private final PayOsGateway gateway;
  private final AdPaymentSyncService sync;
  private final PayOsProperties properties;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public PartnerAdService(
      AdPackageRepository packages,
      SubscriptionRepository subscriptions,
      SubscriptionRenewalWindowRepository windows,
      PaymentRepository payments,
      AdvertisementRepository advertisements,
      BusinessProfileRepository profiles,
      BusinessListingRepository listings,
      PlaceImageRepository images,
      PayOsGateway gateway,
      AdPaymentSyncService sync,
      PayOsProperties properties,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.packages = packages;
    this.subscriptions = subscriptions;
    this.windows = windows;
    this.payments = payments;
    this.advertisements = advertisements;
    this.profiles = profiles;
    this.listings = listings;
    this.images = images;
    this.gateway = gateway;
    this.sync = sync;
    this.properties = properties;
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  // ===== Xem =====

  @Transactional(readOnly = true)
  public List<AdDtos.PackageItem> activePackages() {
    return packages.findByStatusOrderBySortOrderAscPackageIdAsc(ACTIVE).stream()
        .map(AdMappers::toPackage)
        .toList();
  }

  @Transactional(readOnly = true)
  public AdDtos.PartnerState state(Long userId) {
    BusinessProfile business = business(userId);
    Long businessId = business.getBusinessId();
    Optional<Subscription> active =
        subscriptions.findFirstByBusinessBusinessIdAndStatus(businessId, ACTIVE);
    AdDtos.SubscriptionInfo info =
        active.map(item -> AdMappers.toInfo(item, usedSlots(item), clock.instant())).orElse(null);
    Instant now = clock.instant();
    AdDtos.PaymentItem pending =
        payments
            .findBySubscriptionBusinessBusinessIdAndStatusOrderByPaymentIdDesc(businessId, PENDING)
            .stream()
            .filter(payment -> hasLiveLink(payment, now))
            .findFirst()
            .map(AdMappers::toPayment)
            .orElse(null);
    boolean renewable =
        active
            .flatMap(
                item ->
                    windows.findFirstBySubscriptionSubscriptionIdAndStatus(
                        item.getSubscriptionId(), "OPEN"))
            .isPresent();
    long eligible =
        listings.findByBusinessUserUserIdOrderByListingIdDesc(userId).stream()
            .filter(listing -> ineligibleReason(listing) == null)
            .count();
    return new AdDtos.PartnerState(info, pending, renewable, eligible);
  }

  @Transactional(readOnly = true)
  public PageResponse<AdDtos.PaymentItem> payments(Long userId, Pageable pageable) {
    Page<Payment> page =
        payments.findBySubscriptionBusinessBusinessIdOrderByPaymentIdDesc(
            business(userId).getBusinessId(), pageable);
    return PageResponse.from(page.map(AdMappers::toPayment));
  }

  /** Trạng thái đơn sau khi quay về từ PayOS: luôn hỏi lại PayOS, không tin tham số trên URL. */
  public AdDtos.PaymentItem paymentStatus(Long userId, long orderCode) {
    Payment payment = ownedPayment(userId, orderCode);
    if (PENDING.equals(payment.getStatus())) {
      try {
        sync.reconcile(orderCode);
      } catch (ApiException e) {
        // PayOS tạm lỗi: trả trạng thái hiện có, trang kết quả sẽ hỏi lại
      }
    }
    return AdMappers.toPayment(ownedPayment(userId, orderCode));
  }

  public AdDtos.PaymentItem cancelPayment(Long userId, long orderCode) {
    Payment payment = ownedPayment(userId, orderCode);
    if (!PENDING.equals(payment.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, "Đơn này không còn chờ thanh toán");
    }
    sync.cancelByPartner(orderCode);
    return AdMappers.toPayment(ownedPayment(userId, orderCode));
  }

  // ===== Mua gói =====

  public AdDtos.CheckoutResponse checkout(Long userId, Long packageId) {
    return startPayment(() -> prepareInitial(userId, packageId));
  }

  public AdDtos.CheckoutResponse renew(Long userId) {
    return startPayment(() -> prepareRenewal(userId));
  }

  /** Ghi đơn trong một transaction, gọi PayOS bên ngoài để không giữ kết nối DB khi chờ mạng. */
  private AdDtos.CheckoutResponse startPayment(java.util.function.Supplier<Payment> prepare) {
    Payment payment;
    try {
      payment = transaction.execute(status -> prepare.get());
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(HttpStatus.CONFLICT, "Đơn đang được tạo, vui lòng thử lại sau giây lát");
    }
    if (payment.getCheckoutUrl() != null) {
      return new AdDtos.CheckoutResponse(
          payment.getTransactionReference(), payment.getCheckoutUrl());
    }
    long orderCode = Long.parseLong(payment.getTransactionReference());
    String resultUrl = trimSlash(properties.webUrl()) + RESULT_PATH;
    PayOsGateway.PaymentLink link;
    try {
      link =
          gateway.createLink(
              orderCode,
              payment.getAmount().longValueExact(),
              "ST" + orderCode,
              resultUrl,
              resultUrl,
              payment.getLinkExpiresAt().getEpochSecond());
    } catch (ApiException e) {
      markLinkFailed(payment.getPaymentId());
      throw e;
    }
    transaction.executeWithoutResult(
        status -> {
          Payment saved = payments.findById(payment.getPaymentId()).orElseThrow();
          saved.setPaymentLinkId(link.paymentLinkId());
          saved.setCheckoutUrl(link.checkoutUrl());
        });
    return new AdDtos.CheckoutResponse(payment.getTransactionReference(), link.checkoutUrl());
  }

  private void markLinkFailed(Long paymentId) {
    transaction.executeWithoutResult(
        status -> {
          Payment saved = payments.findById(paymentId).orElseThrow();
          saved.setStatus(AdPaymentResultService.FAILED);
          Subscription subscription = saved.getSubscription();
          if (PENDING.equals(subscription.getStatus())) {
            subscription.setStatus(AdPaymentResultService.CANCELLED);
            subscription.setCancelReason("Không tạo được link thanh toán");
          }
        });
  }

  private Payment prepareInitial(Long userId, Long packageId) {
    BusinessProfile business = activeBusiness(userId);
    Long businessId = business.getBusinessId();
    if (subscriptions.existsByBusinessBusinessIdAndStatus(businessId, ACTIVE)) {
      throw new ApiException(HttpStatus.CONFLICT, "Bạn đang có gói chạy, chỉ có thể gia hạn");
    }
    boolean hasEligible =
        listings.findByBusinessUserUserIdOrderByListingIdDesc(userId).stream()
            .anyMatch(listing -> ineligibleReason(listing) == null);
    if (!hasEligible) {
      throw new ApiException(
          HttpStatus.CONFLICT, "Bạn cần có ít nhất 1 địa điểm đã được duyệt trước khi mua gói");
    }
    AdPackage adPackage =
        packages
            .findById(packageId)
            .filter(item -> ACTIVE.equals(item.getStatus()))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Gói không còn được bán"));

    Optional<Payment> reusable = reuseOrClosePending(businessId);
    if (reusable.isPresent()) {
      AdPackage pendingPackage = reusable.get().getSubscription().getAdPackage();
      if (pendingPackage == null || !packageId.equals(pendingPackage.getPackageId())) {
        throw new ApiException(
            HttpStatus.CONFLICT, "Bạn đang có đơn chờ thanh toán, hãy huỷ đơn đó trước khi đổi gói");
      }
      return reusable.get();
    }

    Instant now = clock.instant();
    Subscription subscription = new Subscription();
    subscription.setBusiness(business);
    subscription.setAdPackage(adPackage);
    subscription.setPackageName(adPackage.getName());
    subscription.setMaxPlaces(adPackage.getMaxPlaces());
    subscription.setDurationDays(adPackage.getDurationDays());
    subscription.setMonthlyFee(adPackage.getPrice());
    // Ngày tạm (cột bắt buộc có giá trị), được ghi lại khi thanh toán xong
    subscription.setStartDate(now);
    subscription.setEndDate(now.plus(Duration.ofDays(adPackage.getDurationDays())));
    subscription = subscriptions.saveAndFlush(subscription);
    return newPayment(subscription, business, AdPaymentResultService.INITIAL, null, now);
  }

  private Payment prepareRenewal(Long userId) {
    BusinessProfile business = activeBusiness(userId);
    Subscription subscription =
        subscriptions
            .lockActiveByBusiness(business.getBusinessId())
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Bạn chưa có gói đang chạy"));
    SubscriptionRenewalWindow window =
        windows
            .findFirstBySubscriptionSubscriptionIdAndStatus(
                subscription.getSubscriptionId(), "OPEN")
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.CONFLICT, "Chỉ gia hạn được trong 7 ngày cuối của gói"));
    Optional<Payment> reusable = reuseOrClosePending(business.getBusinessId());
    if (reusable.isPresent()) {
      return reusable.get();
    }
    return newPayment(
        subscription, business, AdPaymentResultService.RENEWAL, window, clock.instant());
  }

  /** Đơn đang chờ còn hạn thì dùng lại link; đơn quá hạn thì đóng để tạo đơn mới. */
  private Optional<Payment> reuseOrClosePending(Long businessId) {
    Instant now = clock.instant();
    for (Payment pending :
        payments.findBySubscriptionBusinessBusinessIdAndStatusOrderByPaymentIdDesc(
            businessId, PENDING)) {
      if (hasLiveLink(pending, now)) {
        return Optional.of(pending);
      }
      pending.setStatus(AdPaymentResultService.CANCELLED);
      Subscription subscription = pending.getSubscription();
      if (PENDING.equals(subscription.getStatus())) {
        subscription.setStatus(AdPaymentResultService.CANCELLED);
        subscription.setCancelReason("Đơn thanh toán đã hết hạn");
      }
    }
    payments.flush();
    return Optional.empty();
  }

  private Payment newPayment(
      Subscription subscription,
      BusinessProfile business,
      String type,
      SubscriptionRenewalWindow window,
      Instant now) {
    Payment payment = new Payment();
    payment.setSubscription(subscription);
    payment.setUser(business.getUser());
    payment.setAmount(subscription.getMonthlyFee());
    payment.setCurrency(subscription.getCurrency());
    payment.setPaymentGateway(AdPaymentResultService.GATEWAY);
    payment.setPaymentMethod("VIETQR");
    payment.setTransactionReference(String.valueOf(payments.nextOrderCode()));
    payment.setPaymentType(type);
    payment.setRenewalWindow(window);
    payment.setLinkExpiresAt(
        now.plus(properties.linkTtl() == null ? DEFAULT_LINK_TTL : properties.linkTtl()));
    return payments.saveAndFlush(payment);
  }

  private static boolean hasLiveLink(Payment payment, Instant now) {
    return payment.getCheckoutUrl() != null
        && payment.getLinkExpiresAt() != null
        && now.isBefore(payment.getLinkExpiresAt());
  }

  // ===== Chọn địa điểm =====

  @Transactional(readOnly = true)
  public AdDtos.SlotsResponse slots(Long userId) {
    BusinessProfile business = business(userId);
    Optional<Subscription> active =
        subscriptions.findFirstByBusinessBusinessIdAndStatus(business.getBusinessId(), ACTIVE);
    Map<Long, Advertisement> current =
        active.map(item -> currentAds(item.getSubscriptionId())).orElse(Map.of());
    List<BusinessListing> mine = listings.findByBusinessUserUserIdOrderByListingIdDesc(userId);
    Map<Long, String> covers = covers(mine);
    List<AdDtos.SlotPlace> places =
        mine.stream()
            .map(
                listing -> {
                  Place place = listing.getPlace();
                  Advertisement ad = current.get(listing.getListingId());
                  String reason = ineligibleReason(listing);
                  return new AdDtos.SlotPlace(
                      listing.getListingId(),
                      place.getPlaceId(),
                      place.getName(),
                      place.getAddress(),
                      place.getProvince() == null ? null : place.getProvince().getName(),
                      covers.get(place.getPlaceId()),
                      reason == null,
                      reason,
                      ad != null,
                      ad != null && SUSPENDED.equals(ad.getStatus()),
                      ad == null ? null : ad.getSuspendReason());
                })
            .toList();
    return new AdDtos.SlotsResponse(active.map(Subscription::getMaxPlaces).orElse(0), places);
  }

  @Transactional
  public AdDtos.SlotsResponse saveSlots(Long userId, List<Long> listingIds) {
    BusinessProfile business = activeBusiness(userId);
    Subscription subscription =
        subscriptions
            .lockActiveByBusiness(business.getBusinessId())
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Bạn chưa có gói đang chạy"));
    Set<Long> wanted = new HashSet<>(listingIds);
    if (wanted.size() != listingIds.size()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Danh sách địa điểm bị trùng");
    }
    if (wanted.size() > subscription.getMaxPlaces()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Gói của bạn chỉ quảng cáo được tối đa " + subscription.getMaxPlaces() + " địa điểm");
    }

    Instant now = clock.instant();
    Map<Long, Advertisement> current = currentAds(subscription.getSubscriptionId());
    current.forEach(
        (listingId, ad) -> {
          if (!wanted.contains(listingId)) {
            ad.setStatus("EXPIRED");
            ad.setEndAt(now);
          }
        });
    advertisements.flush();

    Map<Long, BusinessListing> mine =
        listings.findByBusinessUserUserIdOrderByListingIdDesc(userId).stream()
            .collect(Collectors.toMap(BusinessListing::getListingId, Function.identity()));
    for (Long listingId : wanted) {
      if (current.containsKey(listingId)) {
        continue;
      }
      BusinessListing listing = mine.get(listingId);
      if (listing == null) {
        throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy địa điểm");
      }
      String reason = ineligibleReason(listing);
      if (reason != null) {
        throw new ApiException(
            HttpStatus.CONFLICT, "\"" + listing.getPlace().getName() + "\": " + reason);
      }
      Advertisement ad = new Advertisement();
      ad.setListing(listing);
      ad.setSubscription(subscription);
      ad.setTitle(listing.getPlace().getName());
      ad.setStatus(PUBLISHED);
      ad.setStartAt(now);
      ad.setEndAt(subscription.getEndDate());
      advertisements.save(ad);
    }
    advertisements.flush();
    return slots(userId);
  }

  // ===== Helpers =====

  /** null = đủ điều kiện quảng cáo */
  private static String ineligibleReason(BusinessListing listing) {
    if (!APPROVED.equals(listing.getStatus())) {
      return switch (listing.getStatus()) {
        case "PENDING" -> "Chờ duyệt";
        case "REJECTED" -> "Bị từ chối";
        default -> "Đang tạm ẩn";
      };
    }
    return ACTIVE.equals(listing.getPlace().getStatus()) ? null : "Địa điểm đang tạm ngưng";
  }

  private Map<Long, Advertisement> currentAds(Long subscriptionId) {
    return advertisements
        .findBySubscriptionSubscriptionIdAndStatusIn(subscriptionId, AdSubscriptionOps.SLOT_STATUSES)
        .stream()
        .collect(Collectors.toMap(ad -> ad.getListing().getListingId(), Function.identity()));
  }

  private long usedSlots(Subscription subscription) {
    return advertisements
        .findBySubscriptionSubscriptionIdAndStatusIn(
            subscription.getSubscriptionId(), AdSubscriptionOps.SLOT_STATUSES)
        .size();
  }

  private Map<Long, String> covers(List<BusinessListing> mine) {
    List<Long> placeIds = mine.stream().map(item -> item.getPlace().getPlaceId()).toList();
    Map<Long, String> covers = new HashMap<>();
    if (!placeIds.isEmpty()) {
      for (PlaceImage image : images.findForPlaces(placeIds)) {
        covers.putIfAbsent(image.getPlace().getPlaceId(), image.getImageUrl());
      }
    }
    return covers;
  }

  private Payment ownedPayment(Long userId, long orderCode) {
    return payments
        .findByPaymentGatewayAndTransactionReference(
            AdPaymentResultService.GATEWAY, String.valueOf(orderCode))
        .filter(
            payment ->
                payment.getSubscription().getBusiness().getUser().getUserId().equals(userId))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn thanh toán"));
  }

  private BusinessProfile business(Long userId) {
    return profiles
        .findByUserUserId(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bạn chưa có hồ sơ đối tác"));
  }

  private BusinessProfile activeBusiness(Long userId) {
    BusinessProfile business = business(userId);
    if (!ACTIVE.equals(business.getStatus())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Hồ sơ đối tác chưa hoạt động");
    }
    return business;
  }

  private static String trimSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
