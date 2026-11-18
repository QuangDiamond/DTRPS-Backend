package com.dtrps.modules.payment.service;

import com.dtrps.modules.advertisement.entity.Advertisement;
import com.dtrps.modules.advertisement.repository.AdvertisementRepository;
import com.dtrps.modules.payment.entity.Subscription;
import com.dtrps.modules.payment.entity.SubscriptionRenewalWindow;
import com.dtrps.modules.payment.repository.SubscriptionRenewalWindowRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Kết thúc một đăng ký (hết hạn / bị huỷ): dừng quảng cáo và đóng cửa sổ gia hạn. */
@Component
class AdSubscriptionOps {
  static final List<String> SLOT_STATUSES = List.of("PUBLISHED", "SUSPENDED");

  private final AdvertisementRepository advertisements;
  private final SubscriptionRenewalWindowRepository windows;
  private final Clock clock;

  AdSubscriptionOps(
      AdvertisementRepository advertisements,
      SubscriptionRenewalWindowRepository windows,
      Clock clock) {
    this.advertisements = advertisements;
    this.windows = windows;
    this.clock = clock;
  }

  /** {@code status}: EXPIRED hoặc CANCELLED */
  void end(Subscription subscription, String status, String reason) {
    Instant now = clock.instant();
    subscription.setStatus(status);
    subscription.setCancelReason(reason);
    for (Advertisement ad :
        advertisements.findBySubscriptionSubscriptionIdAndStatusIn(
            subscription.getSubscriptionId(), SLOT_STATUSES)) {
      ad.setStatus("EXPIRED");
      ad.setEndAt(now);
    }
    for (SubscriptionRenewalWindow window :
        windows.findBySubscriptionSubscriptionIdAndStatus(
            subscription.getSubscriptionId(), "OPEN")) {
      window.setStatus("EXPIRED".equals(status) ? "EXPIRED" : "CANCELLED");
    }
  }

  /** Gia hạn: quảng cáo đang chiếm lượt chạy tới ngày hết hạn mới */
  void extendAds(Subscription subscription) {
    for (Advertisement ad :
        advertisements.findBySubscriptionSubscriptionIdAndStatusIn(
            subscription.getSubscriptionId(), SLOT_STATUSES)) {
      ad.setEndAt(subscription.getEndDate());
    }
  }
}
