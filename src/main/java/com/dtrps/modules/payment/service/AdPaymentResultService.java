package com.dtrps.modules.payment.service;

import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.payment.entity.Payment;
import com.dtrps.modules.payment.entity.Subscription;
import com.dtrps.modules.payment.repository.PaymentRepository;
import com.dtrps.modules.payment.repository.SubscriptionRenewalWindowRepository;
import com.dtrps.modules.payment.repository.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Áp dụng kết quả thanh toán. Webhook, trang kết quả, nút đối soát và job nền đều đi qua đây; dòng
 * giao dịch bị khoá nên chạy lại nhiều lần vẫn chỉ kích hoạt một lần.
 */
@Service
public class AdPaymentResultService {
  private static final Logger log = LoggerFactory.getLogger(AdPaymentResultService.class);

  static final String GATEWAY = "PAYOS";
  static final String PENDING = "PENDING";
  static final String SUCCESS = "SUCCESS";
  static final String CANCELLED = "CANCELLED";
  static final String FAILED = "FAILED";
  static final String ACTIVE = "ACTIVE";
  static final String INITIAL = "INITIAL";
  static final String RENEWAL = "RENEWAL";
  static final String AUDIT_ENTITY = "payments";

  /** Kết quả từ PayOS; {@code amount}: số tiền thực nhận (chỉ có nghĩa khi PAID) */
  public record GatewayResult(String status, long amount, String reference) {}

  private final PaymentRepository payments;
  private final SubscriptionRepository subscriptions;
  private final SubscriptionRenewalWindowRepository windows;
  private final AdSubscriptionOps subscriptionOps;
  private final AuditService auditService;
  private final NotificationService notifications;
  private final Clock clock;

  public AdPaymentResultService(
      PaymentRepository payments,
      SubscriptionRepository subscriptions,
      SubscriptionRenewalWindowRepository windows,
      AdSubscriptionOps subscriptionOps,
      AuditService auditService,
      NotificationService notifications,
      Clock clock) {
    this.payments = payments;
    this.subscriptions = subscriptions;
    this.windows = windows;
    this.subscriptionOps = subscriptionOps;
    this.auditService = auditService;
    this.notifications = notifications;
    this.clock = clock;
  }

  @Transactional
  public void apply(long orderCode, GatewayResult result) {
    Optional<Payment> found = payments.lockByReference(GATEWAY, String.valueOf(orderCode));
    if (found.isEmpty()) {
      // Ví dụ giao dịch mẫu PayOS gửi khi đăng ký webhook
      log.info("PayOS result for unknown order {}", orderCode);
      return;
    }
    Payment payment = found.get();
    payment.setGatewayStatus(result.status());
    if (result.reference() != null && !result.reference().isBlank()) {
      payment.setBankReference(result.reference());
    }
    switch (result.status()) {
      case "PAID" -> onPaid(payment, result.amount());
      case "CANCELLED", "EXPIRED" -> onCancelled(payment, result.status());
      case "PENDING", "PROCESSING" -> {
        // Chờ kết quả tiếp theo
      }
      default -> {
        if (PENDING.equals(payment.getStatus())) {
          flagForReview(payment, "PayOS trả trạng thái lạ: " + result.status());
        }
      }
    }
  }

  private void onPaid(Payment payment, long amountPaid) {
    if (SUCCESS.equals(payment.getStatus()) || "REFUNDED".equals(payment.getStatus())) {
      return;
    }
    if (payment.getAmount().compareTo(BigDecimal.valueOf(amountPaid)) != 0) {
      flagForReview(
          payment, "Sai số tiền: cần " + payment.getAmount() + ", PayOS báo " + amountPaid);
      return;
    }
    Instant now = clock.instant();
    payment.setStatus(SUCCESS);
    payment.setPaidAt(now);

    Subscription subscription =
        subscriptions.lockById(payment.getSubscription().getSubscriptionId()).orElseThrow();
    boolean activated =
        RENEWAL.equals(payment.getPaymentType())
            ? renew(payment, subscription)
            : activate(payment, subscription, now);

    Long userId = payment.getUser().getUserId();
    auditService.record(
        userId, "AD_PAYMENT_SUCCESS", AUDIT_ENTITY, payment.getPaymentId(), payment.getAmount().toPlainString());
    if (!activated) {
      return;
    }
    Notice notice =
        new Notice(
            "AD_PAYMENT_SUCCESS",
            "Thanh toán thành công",
            RENEWAL.equals(payment.getPaymentType())
                ? "\"" + subscription.getPackageName() + "\" đã được gia hạn."
                : "\"" + subscription.getPackageName() + "\" đã kích hoạt. Hãy chọn địa điểm quảng cáo.",
            "/partner/ads",
            data(payment));
    notifications.notifyUser(userId, notice);
    notifications.notifyAdmins(
        new Notice(
            "AD_PAYMENT_SUCCESS",
            "Đối tác vừa mua gói quảng cáo",
            subscription.getBusiness().getBusinessName()
                + " đã thanh toán \""
                + subscription.getPackageName()
                + "\".",
            "/admin/payments",
            data(payment)),
        null);
  }

  /** Mua mới: gói tính từ lúc thanh toán. Đã có gói khác đang chạy thì để admin hoàn tiền. */
  private boolean activate(Payment payment, Subscription subscription, Instant now) {
    if (ACTIVE.equals(subscription.getStatus())) {
      return false;
    }
    boolean hasOther =
        subscriptions.existsByBusinessBusinessIdAndStatus(
            subscription.getBusiness().getBusinessId(), ACTIVE);
    if (hasOther) {
      subscription.setStatus(CANCELLED);
      flagForReview(payment, "Trả tiền khi đối tác đã có gói khác đang chạy, cần hoàn tiền");
      return false;
    }
    subscription.setStatus(ACTIVE);
    subscription.setCancelReason(null);
    subscription.setStartDate(now);
    subscription.setEndDate(now.plus(Duration.ofDays(subscription.getDurationDays())));
    return true;
  }

  /** Gia hạn: cộng thêm từ ngày hết hạn cũ. Gói đã kết thúc thì để admin hoàn tiền. */
  private boolean renew(Payment payment, Subscription subscription) {
    if (!ACTIVE.equals(subscription.getStatus())) {
      flagForReview(payment, "Trả tiền gia hạn khi gói đã kết thúc, cần hoàn tiền");
      return false;
    }
    subscription.setEndDate(
        subscription.getEndDate().plus(Duration.ofDays(subscription.getDurationDays())));
    subscriptionOps.extendAds(subscription);
    windows
        .findFirstBySubscriptionSubscriptionIdAndStatus(subscription.getSubscriptionId(), "OPEN")
        .ifPresent(window -> window.setStatus("USED"));
    return true;
  }

  private void onCancelled(Payment payment, String gatewayStatus) {
    if (!PENDING.equals(payment.getStatus())) {
      return;
    }
    payment.setStatus(CANCELLED);
    Subscription subscription = payment.getSubscription();
    if (INITIAL.equals(payment.getPaymentType()) && PENDING.equals(subscription.getStatus())) {
      subscription.setStatus(CANCELLED);
      subscription.setCancelReason("Đơn thanh toán đã huỷ hoặc hết hạn");
    }
    notifications.notifyUser(
        payment.getUser().getUserId(),
        new Notice(
            "AD_PAYMENT_CANCELLED",
            "Đơn thanh toán đã kết thúc",
            "EXPIRED".equals(gatewayStatus)
                ? "Đơn thanh toán đã hết hạn. Bạn có thể mua lại gói bất cứ lúc nào."
                : "Đơn thanh toán đã được huỷ.",
            "/partner/ads",
            data(payment)));
  }

  private void flagForReview(Payment payment, String note) {
    boolean first = !payment.isNeedsReview();
    payment.setNeedsReview(true);
    payment.setReviewNote(note);
    if (first) {
      notifications.notifyAdmins(
          new Notice(
              "AD_PAYMENT_REVIEW",
              "Giao dịch cần kiểm tra",
              "Đơn " + payment.getTransactionReference() + ": " + note,
              "/admin/payments?review=true",
              data(payment)),
          null);
    }
  }

  private static Map<String, Object> data(Payment payment) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("paymentId", payment.getPaymentId());
    data.put("orderCode", payment.getTransactionReference());
    data.put("status", payment.getStatus());
    return data;
  }
}
