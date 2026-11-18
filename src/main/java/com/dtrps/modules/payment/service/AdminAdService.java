package com.dtrps.modules.payment.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.advertisement.entity.Advertisement;
import com.dtrps.modules.advertisement.repository.AdvertisementRepository;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.payment.dto.AdDtos;
import com.dtrps.modules.payment.entity.AdPackage;
import com.dtrps.modules.payment.entity.Payment;
import com.dtrps.modules.payment.entity.Subscription;
import com.dtrps.modules.payment.repository.AdPackageRepository;
import com.dtrps.modules.payment.repository.PaymentRepository;
import com.dtrps.modules.payment.repository.SubscriptionRepository;
import com.dtrps.modules.user.repository.UserRepository;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin quản lý bảng giá, giao dịch, gói đang chạy và quảng cáo. */
@Service
public class AdminAdService {
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final String ACTIVE = "ACTIVE";
  private static final String SUCCESS = "SUCCESS";
  private static final String SUSPENDED = "SUSPENDED";
  private static final String PUBLISHED = "PUBLISHED";
  private static final String PARTNER_ADS = "/partner/ads";

  private final AdPackageRepository packages;
  private final PaymentRepository payments;
  private final SubscriptionRepository subscriptions;
  private final AdvertisementRepository advertisements;
  private final UserRepository users;
  private final AdSubscriptionOps subscriptionOps;
  private final AuditService auditService;
  private final NotificationService notifications;
  private final Clock clock;

  public AdminAdService(
      AdPackageRepository packages,
      PaymentRepository payments,
      SubscriptionRepository subscriptions,
      AdvertisementRepository advertisements,
      UserRepository users,
      AdSubscriptionOps subscriptionOps,
      AuditService auditService,
      NotificationService notifications,
      Clock clock) {
    this.packages = packages;
    this.payments = payments;
    this.subscriptions = subscriptions;
    this.advertisements = advertisements;
    this.users = users;
    this.subscriptionOps = subscriptionOps;
    this.auditService = auditService;
    this.notifications = notifications;
    this.clock = clock;
  }

  // ===== Bảng giá =====

  @Transactional(readOnly = true)
  public List<AdDtos.PackageItem> packages() {
    return packages.findAllByOrderBySortOrderAscPackageIdAsc().stream()
        .map(AdMappers::toPackage)
        .toList();
  }

  @Transactional
  public AdDtos.PackageItem createPackage(Long adminId, AdDtos.PackageRequest request) {
    AdPackage item = new AdPackage();
    item.setCode("PKG_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
    apply(item, request);
    packages.save(item);
    auditService.record(adminId, "AD_PACKAGE_CREATED", "ad_packages", item.getPackageId());
    return AdMappers.toPackage(item);
  }

  /** Đăng ký đã mua giữ thông tin lúc mua nên sửa gói chỉ ảnh hưởng lượt mua sau. */
  @Transactional
  public AdDtos.PackageItem updatePackage(Long adminId, Long id, AdDtos.PackageRequest request) {
    AdPackage item = requirePackage(id);
    apply(item, request);
    auditService.record(adminId, "AD_PACKAGE_UPDATED", "ad_packages", id);
    return AdMappers.toPackage(item);
  }

  @Transactional
  public AdDtos.PackageItem changePackageStatus(Long adminId, Long id, String status) {
    AdPackage item = requirePackage(id);
    item.setStatus(status);
    auditService.record(adminId, "AD_PACKAGE_STATUS", "ad_packages", id, status);
    return AdMappers.toPackage(item);
  }

  private static void apply(AdPackage item, AdDtos.PackageRequest request) {
    item.setName(request.name().trim());
    item.setDescription(request.description());
    item.setPrice(BigDecimal.valueOf(request.price()));
    item.setDurationDays(request.durationDays());
    item.setMaxPlaces(request.maxPlaces());
    item.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
  }

  private AdPackage requirePackage(Long id) {
    return packages
        .findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy gói"));
  }

  // ===== Giao dịch =====

  @Transactional(readOnly = true)
  public AdDtos.PaymentStats paymentStats() {
    LocalDate firstDay = LocalDate.ofInstant(clock.instant(), VIETNAM).withDayOfMonth(1);
    return new AdDtos.PaymentStats(
        payments.revenueSince(firstDay.atStartOfDay(VIETNAM).toInstant()),
        payments.countByStatus(SUCCESS),
        payments.countByStatus("PENDING"),
        payments.countByNeedsReviewTrue());
  }

  @Transactional(readOnly = true)
  public PageResponse<AdDtos.AdminPayment> payments(
      String keyword,
      String status,
      String type,
      boolean reviewOnly,
      LocalDate from,
      LocalDate to,
      Pageable pageable) {
    Specification<Payment> spec = (root, query, cb) -> cb.conjunction();
    if (hasText(keyword)) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.or(
                      cb.like(cb.lower(root.get("transactionReference")), like),
                      cb.like(
                          cb.lower(
                              root.join("subscription")
                                  .join("business", JoinType.INNER)
                                  .get("businessName")),
                          like)));
    }
    if (hasText(status)) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (hasText(type)) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("paymentType"), type));
    }
    if (reviewOnly) {
      spec = spec.and((root, query, cb) -> cb.isTrue(root.get("needsReview")));
    }
    if (from != null) {
      Instant start = from.atStartOfDay(VIETNAM).toInstant();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
    }
    if (to != null) {
      Instant end = to.plusDays(1).atStartOfDay(VIETNAM).toInstant();
      spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
    }
    Page<Payment> page = payments.findAll(spec, pageable);
    return PageResponse.from(page.map(AdminAdService::toAdminPayment));
  }

  @Transactional(readOnly = true)
  public AdDtos.AdminPayment payment(Long id) {
    return toAdminPayment(requirePayment(id));
  }

  @Transactional(readOnly = true)
  public long orderCodeOf(Long paymentId) {
    return Long.parseLong(requirePayment(paymentId).getTransactionReference());
  }

  /**
   * Admin chuyển khoản tay rồi ghi nhận tại đây. Giao dịch bất thường chưa từng kích hoạt dịch vụ
   * nên không đụng tới gói đang chạy của đối tác.
   */
  @Transactional
  public AdDtos.AdminPayment refund(Long adminId, Long id, String reason, String reference) {
    Payment payment = requirePayment(id);
    if (!SUCCESS.equals(payment.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, "Chỉ hoàn tiền được giao dịch đã thành công");
    }
    payment.setStatus("REFUNDED");
    payment.setRefundReason(reason.trim());
    payment.setRefundReference(reference.trim());
    payment.setRefundedAt(clock.instant());
    payment.setRefundedBy(users.getReferenceById(adminId));
    Subscription subscription = payment.getSubscription();
    if (!payment.isNeedsReview() && ACTIVE.equals(subscription.getStatus())) {
      subscriptionOps.end(subscription, "CANCELLED", "Hoàn tiền: " + reason.trim());
    }
    payment.setNeedsReview(false);
    auditService.record(adminId, "AD_PAYMENT_REFUNDED", "payments", id, reason.trim());
    notifications.notifyUser(
        payment.getUser().getUserId(),
        new Notice(
            "AD_PAYMENT_REFUNDED",
            "Đã hoàn tiền đơn " + payment.getTransactionReference(),
            "Lý do: " + reason.trim(),
            PARTNER_ADS,
            Map.of("paymentId", id)));
    return toAdminPayment(payment);
  }

  private Payment requirePayment(Long id) {
    return payments
        .findWithDetailsByPaymentId(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"));
  }

  private static AdDtos.AdminPayment toAdminPayment(Payment item) {
    Subscription subscription = item.getSubscription();
    return new AdDtos.AdminPayment(
        item.getPaymentId(),
        item.getTransactionReference(),
        subscription.getBusiness().getBusinessId(),
        subscription.getBusiness().getBusinessName(),
        subscription.getPackageName(),
        item.getAmount(),
        item.getPaymentType(),
        item.getStatus(),
        item.getGatewayStatus(),
        item.getBankReference(),
        item.isNeedsReview(),
        item.getReviewNote(),
        item.getRefundReference(),
        item.getRefundReason(),
        item.getRefundedAt(),
        item.getPaidAt(),
        item.getCreatedAt());
  }

  // ===== Gói đang chạy =====

  @Transactional(readOnly = true)
  public PageResponse<AdDtos.AdminSubscription> subscriptions(
      String keyword, String status, Pageable pageable) {
    Specification<Subscription> spec =
        (root, query, cb) -> cb.notEqual(root.get("status"), "PENDING");
    if (hasText(keyword)) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.like(cb.lower(root.join("business").get("businessName")), like));
    }
    if (hasText(status)) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    Page<Subscription> page = subscriptions.findAll(spec, pageable);
    Map<Long, Long> used = new HashMap<>();
    List<Long> ids = page.getContent().stream().map(Subscription::getSubscriptionId).toList();
    if (!ids.isEmpty()) {
      advertisements.usedSlots(ids).forEach(row -> used.put((Long) row[0], (Long) row[1]));
    }
    Instant now = clock.instant();
    return PageResponse.from(
        page.map(item -> toAdminSubscription(item, used.getOrDefault(item.getSubscriptionId(), 0L), now)));
  }

  @Transactional
  public void cancelSubscription(Long adminId, Long id, String reason) {
    Subscription subscription =
        subscriptions
            .lockById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy gói"));
    if (!ACTIVE.equals(subscription.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, "Gói này không còn hoạt động");
    }
    subscriptionOps.end(subscription, "CANCELLED", reason.trim());
    auditService.record(adminId, "AD_SUBSCRIPTION_CANCELLED", "subscriptions", id, reason.trim());
    notifications.notifyUser(
        subscription.getBusiness().getUser().getUserId(),
        new Notice(
            "AD_SUBSCRIPTION_CANCELLED",
            "Gói quảng cáo đã bị huỷ",
            "Lý do: " + reason.trim(),
            PARTNER_ADS,
            Map.of("subscriptionId", id)));
  }

  private static AdDtos.AdminSubscription toAdminSubscription(
      Subscription item, long used, Instant now) {
    return new AdDtos.AdminSubscription(
        item.getSubscriptionId(),
        item.getBusiness().getBusinessId(),
        item.getBusiness().getBusinessName(),
        item.getPackageName(),
        item.getMonthlyFee(),
        item.getMaxPlaces(),
        used,
        item.getStatus(),
        item.getStartDate(),
        item.getEndDate(),
        ACTIVE.equals(item.getStatus()) ? AdMappers.daysLeft(item, now) : 0,
        item.getCancelReason());
  }

  // ===== Quảng cáo =====

  @Transactional(readOnly = true)
  public PageResponse<AdDtos.AdminAdvertisement> advertisements(
      String keyword, String status, Pageable pageable) {
    Specification<Advertisement> spec =
        (root, query, cb) -> cb.isNotNull(root.get("subscription"));
    if (hasText(keyword)) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) -> {
                var listing = root.join("listing");
                return cb.or(
                    cb.like(cb.lower(listing.join("place").get("name")), like),
                    cb.like(cb.lower(listing.join("business").get("businessName")), like));
              });
    }
    if (hasText(status)) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    return PageResponse.from(advertisements.findAll(spec, pageable).map(AdminAdService::toAdminAd));
  }

  @Transactional
  public AdDtos.AdminAdvertisement changeAdStatus(
      Long adminId, Long id, String status, String reason) {
    Advertisement ad =
        advertisements
            .findWithDetailsByAdvertisementId(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy quảng cáo"));
    String required = SUSPENDED.equals(status) ? PUBLISHED : SUSPENDED;
    if (!required.equals(ad.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, "Quảng cáo không ở trạng thái phù hợp");
    }
    if (PUBLISHED.equals(status) && !ACTIVE.equals(ad.getSubscription().getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, "Gói của quảng cáo này không còn hoạt động");
    }
    boolean hiding = SUSPENDED.equals(status);
    ad.setStatus(status);
    ad.setSuspendReason(hiding ? reason.trim() : null);
    auditService.record(adminId, hiding ? "AD_SUSPENDED" : "AD_RESTORED", "advertisements", id, reason.trim());
    String place = ad.getListing().getPlace().getName();
    notifications.notifyUser(
        ad.getListing().getBusiness().getUser().getUserId(),
        new Notice(
            hiding ? "AD_SUSPENDED" : "AD_RESTORED",
            hiding ? "Quảng cáo đã bị gỡ" : "Quảng cáo đã được bật lại",
            "\"" + place + "\". Lý do: " + reason.trim(),
            PARTNER_ADS,
            Map.of("advertisementId", id)));
    return toAdminAd(ad);
  }

  private static AdDtos.AdminAdvertisement toAdminAd(Advertisement ad) {
    return new AdDtos.AdminAdvertisement(
        ad.getAdvertisementId(),
        ad.getListing().getPlace().getPlaceId(),
        ad.getListing().getPlace().getName(),
        ad.getListing().getBusiness().getBusinessId(),
        ad.getListing().getBusiness().getBusinessName(),
        ad.getSubscription().getPackageName(),
        ad.getStatus(),
        ad.getSuspendReason(),
        ad.getStartAt(),
        ad.getEndAt());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
