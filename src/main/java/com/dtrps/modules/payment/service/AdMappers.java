package com.dtrps.modules.payment.service;

import com.dtrps.modules.payment.dto.AdDtos;
import com.dtrps.modules.payment.entity.AdPackage;
import com.dtrps.modules.payment.entity.Payment;
import com.dtrps.modules.payment.entity.Subscription;
import java.time.Duration;
import java.time.Instant;

/** Đổi entity sang DTO cho phía đối tác và admin. */
final class AdMappers {
  private static final long SECONDS_PER_DAY = Duration.ofDays(1).toSeconds();

  private AdMappers() {}

  static AdDtos.PackageItem toPackage(AdPackage item) {
    return new AdDtos.PackageItem(
        item.getPackageId(),
        item.getName(),
        item.getDescription(),
        item.getPrice(),
        item.getDurationDays(),
        item.getMaxPlaces(),
        item.getStatus(),
        item.getSortOrder());
  }

  static AdDtos.SubscriptionInfo toInfo(Subscription item, long usedSlots, Instant now) {
    return new AdDtos.SubscriptionInfo(
        item.getSubscriptionId(),
        item.getPackageName(),
        item.getMonthlyFee(),
        item.getMaxPlaces(),
        item.getDurationDays(),
        item.getStatus(),
        item.getStartDate(),
        item.getEndDate(),
        daysLeft(item, now),
        usedSlots);
  }

  static AdDtos.PaymentItem toPayment(Payment item) {
    return new AdDtos.PaymentItem(
        item.getPaymentId(),
        item.getTransactionReference(),
        item.getSubscription().getPackageName(),
        item.getAmount(),
        item.getCurrency(),
        item.getPaymentType(),
        item.getStatus(),
        "PENDING".equals(item.getStatus()) ? item.getCheckoutUrl() : null,
        item.getLinkExpiresAt(),
        item.getPaidAt(),
        item.getCreatedAt());
  }

  /** Làm tròn lên: còn vài giờ vẫn tính là còn 1 ngày */
  static long daysLeft(Subscription item, Instant now) {
    long seconds = Duration.between(now, item.getEndDate()).toSeconds();
    return seconds <= 0 ? 0 : (seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY;
  }
}
