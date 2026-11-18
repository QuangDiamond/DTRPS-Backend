package com.dtrps.modules.payment.service;

import com.dtrps.modules.payment.entity.Payment;
import com.dtrps.modules.payment.repository.PaymentRepository;
import com.dtrps.modules.payment.service.AdPaymentResultService.GatewayResult;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Đối soát một đơn với PayOS. Gọi mạng nằm ngoài transaction; việc ghi kết quả do {@link
 * AdPaymentResultService} đảm nhiệm.
 */
@Service
public class AdPaymentSyncService {
  private static final Logger log = LoggerFactory.getLogger(AdPaymentSyncService.class);
  private static final String EXPIRED_REASON = "Hết hạn thanh toán";

  private final PaymentRepository payments;
  private final PayOsGateway gateway;
  private final AdPaymentResultService results;
  private final Clock clock;

  public AdPaymentSyncService(
      PaymentRepository payments,
      PayOsGateway gateway,
      AdPaymentResultService results,
      Clock clock) {
    this.payments = payments;
    this.gateway = gateway;
    this.results = results;
    this.clock = clock;
  }

  /** Hỏi PayOS rồi cập nhật. Link đã quá hạn mà vẫn chưa trả thì huỷ luôn. */
  public void reconcile(long orderCode) {
    Payment payment = find(orderCode);
    if (payment == null) {
      return;
    }
    if (payment.getCheckoutUrl() == null) {
      // Chưa từng tạo được link trên PayOS
      if (AdPaymentResultService.PENDING.equals(payment.getStatus())) {
        results.apply(orderCode, new GatewayResult("CANCELLED", 0, null));
      }
      return;
    }
    PayOsGateway.OrderStatus status = gateway.getStatus(orderCode);
    results.apply(orderCode, new GatewayResult(status.status(), status.amountPaid(), null));

    Payment current = find(orderCode);
    boolean stillWaiting =
        current != null
            && AdPaymentResultService.PENDING.equals(current.getStatus())
            && "PENDING".equals(status.status());
    if (stillWaiting
        && current.getLinkExpiresAt() != null
        && !clock.instant().isBefore(current.getLinkExpiresAt())) {
      cancelOnGateway(orderCode, EXPIRED_REASON);
      results.apply(orderCode, new GatewayResult("EXPIRED", 0, null));
    }
  }

  /** Đối tác huỷ đơn: hỏi lại trước để không huỷ nhầm đơn vừa trả tiền. */
  public void cancelByPartner(long orderCode) {
    reconcile(orderCode);
    Payment current = find(orderCode);
    if (current != null && AdPaymentResultService.PENDING.equals(current.getStatus())) {
      cancelOnGateway(orderCode, "Đối tác huỷ đơn");
      results.apply(orderCode, new GatewayResult("CANCELLED", 0, null));
    }
  }

  private void cancelOnGateway(long orderCode, String reason) {
    try {
      gateway.cancel(orderCode, reason);
    } catch (RuntimeException e) {
      // Link hết hạn trên PayOS có thể không huỷ được nữa; nếu khách vẫn trả sẽ xử lý trả tiền muộn
      log.info("PayOS cancel skipped for order {}", orderCode);
    }
  }

  private Payment find(long orderCode) {
    return payments
        .findByPaymentGatewayAndTransactionReference(
            AdPaymentResultService.GATEWAY, String.valueOf(orderCode))
        .orElse(null);
  }
}
