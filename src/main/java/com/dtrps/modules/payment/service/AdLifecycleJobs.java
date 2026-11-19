package com.dtrps.modules.payment.service;

import com.dtrps.modules.admin.service.SystemSettingsService;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.payment.entity.Subscription;
import com.dtrps.modules.payment.entity.SubscriptionRenewalWindow;
import com.dtrps.modules.payment.repository.PaymentRepository;
import com.dtrps.modules.payment.repository.SubscriptionRenewalWindowRepository;
import com.dtrps.modules.payment.repository.SubscriptionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Việc nền của gói quảng cáo. Chỉ bật trên server ({@code PAYOS_JOBS_ENABLED=true}) vì các máy cá
 * nhân dùng chung một DB, chạy cùng lúc sẽ gửi thông báo trùng.
 */
@Component
@ConditionalOnProperty(name = "app.payos.jobs-enabled", havingValue = "true")
public class AdLifecycleJobs {
  private static final Logger log = LoggerFactory.getLogger(AdLifecycleJobs.class);
  private static final Duration RECONCILE_AFTER = Duration.ofMinutes(2);
  private static final int BATCH = 50;
  private static final long FIVE_MINUTES = 300_000;
  private static final long FIFTEEN_MINUTES = 900_000;
  private static final long ONE_HOUR = 3_600_000;

  private final PaymentRepository payments;
  private final SubscriptionRepository subscriptions;
  private final SubscriptionRenewalWindowRepository windows;
  private final AdPaymentSyncService sync;
  private final AdSubscriptionOps subscriptionOps;
  private final NotificationService notifications;
  private final TransactionTemplate transaction;
  private final Clock clock;

  private final SystemSettingsService settings;

  public AdLifecycleJobs(
      PaymentRepository payments,
      SubscriptionRepository subscriptions,
      SubscriptionRenewalWindowRepository windows,
      AdPaymentSyncService sync,
      AdSubscriptionOps subscriptionOps,
      NotificationService notifications,
      PlatformTransactionManager transactionManager,
      SystemSettingsService settings,
      Clock clock) {
    this.settings = settings;
    this.payments = payments;
    this.subscriptions = subscriptions;
    this.windows = windows;
    this.sync = sync;
    this.subscriptionOps = subscriptionOps;
    this.notifications = notifications;
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  /** Phòng webhook bị mất; đồng thời huỷ các link đã quá hạn. */
  @Scheduled(fixedDelay = FIVE_MINUTES)
  void reconcilePendingPayments() {
    Instant before = clock.instant().minus(RECONCILE_AFTER);
    for (String orderCode :
        payments.findReconcilable(
            AdPaymentResultService.GATEWAY, before, PageRequest.of(0, BATCH))) {
      try {
        sync.reconcile(Long.parseLong(orderCode));
      } catch (RuntimeException e) {
        log.warn("Reconcile failed for order {}: {}", orderCode, e.getClass().getSimpleName());
      }
    }
  }

  @Scheduled(fixedDelay = FIFTEEN_MINUTES)
  void expireSubscriptions() {
    for (Long id : subscriptions.findExpiredIds(clock.instant())) {
      transaction.executeWithoutResult(status -> expire(id));
    }
  }

  private void expire(Long id) {
    Subscription subscription = subscriptions.lockById(id).orElse(null);
    if (subscription == null
        || !"ACTIVE".equals(subscription.getStatus())
        || subscription.getEndDate().isAfter(clock.instant())) {
      return;
    }
    subscriptionOps.end(subscription, "EXPIRED", null);
    notifications.notifyUser(
        subscription.getBusiness().getUser().getUserId(),
        new Notice(
            "AD_SUBSCRIPTION_EXPIRED",
            "Gói quảng cáo đã hết hạn",
            "\"" + subscription.getPackageName() + "\" đã hết hạn, quảng cáo đã dừng.",
            "/partner/ads",
            Map.of("subscriptionId", id)));
  }

  @Scheduled(fixedDelay = ONE_HOUR)
  void openRenewalWindows() {
    // Số ngày nhắc gia hạn do admin chỉnh ở Cài đặt hệ thống
    Instant limit = clock.instant().plus(settings.renewalNotice());
    for (Long id : subscriptions.findNeedingRenewalWindow(limit)) {
      transaction.executeWithoutResult(status -> openWindow(id));
    }
  }

  private void openWindow(Long id) {
    Subscription subscription = subscriptions.lockById(id).orElse(null);
    Instant now = clock.instant();
    if (subscription == null
        || !"ACTIVE".equals(subscription.getStatus())
        || !subscription.getEndDate().isAfter(now)) {
      return;
    }
    SubscriptionRenewalWindow window = new SubscriptionRenewalWindow();
    window.setSubscription(subscription);
    window.setOpensAt(now);
    window.setClosesAt(subscription.getEndDate());
    windows.save(window);
    notifications.notifyUser(
        subscription.getBusiness().getUser().getUserId(),
        new Notice(
            "AD_SUBSCRIPTION_EXPIRING",
            "Gói quảng cáo sắp hết hạn",
            "\"" + subscription.getPackageName() + "\" sắp hết hạn. Gia hạn để giữ quảng cáo.",
            "/partner/ads",
            Map.of("subscriptionId", id)));
  }
}
