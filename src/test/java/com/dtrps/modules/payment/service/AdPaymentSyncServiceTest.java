package com.dtrps.modules.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.payment.entity.Payment;
import com.dtrps.modules.payment.repository.PaymentRepository;
import com.dtrps.modules.payment.service.AdPaymentResultService.GatewayResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Unit test AdPaymentSyncService — sheet Payment, UTC038–UTC039 trong Report5.1 Unit Test. */
@ExtendWith(MockitoExtension.class)
class AdPaymentSyncServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final long ORDER = 100_010L;

  @Mock private PaymentRepository payments;
  @Mock private PayOsGateway gateway;
  @Mock private AdPaymentResultService results;

  private AdPaymentSyncService service;
  private Payment payment;

  @BeforeEach
  void setUp() {
    service =
        new AdPaymentSyncService(payments, gateway, results, Clock.fixed(NOW, ZoneOffset.UTC));
    payment = new Payment();
    payment.setTransactionReference(String.valueOf(ORDER));
    payment.setCheckoutUrl("https://pay.payos.vn/web/abc");
    payment.setLinkExpiresAt(NOW.plus(Duration.ofMinutes(10)));
    lenient()
        .when(payments.findByPaymentGatewayAndTransactionReference("PAYOS", String.valueOf(ORDER)))
        .thenReturn(Optional.of(payment));
  }

  @Test
  @DisplayName("UTC038 PayOS báo PAID đúng tiền: chuyển kết quả cho AdPaymentResultService")
  void paid_appliesResult() {
    when(gateway.getStatus(ORDER)).thenReturn(new PayOsGateway.OrderStatus("PAID", 100_000));

    service.reconcile(ORDER);

    verify(results).apply(ORDER, new GatewayResult("PAID", 100_000, null));
    verify(gateway, never()).cancel(anyLong(), anyString());
  }

  @Test
  @DisplayName("UTC039 PayOS lỗi mạng: báo 502, đơn giữ nguyên trạng thái")
  void gatewayError_keepsOrder() {
    when(gateway.getStatus(ORDER))
        .thenThrow(
            new ApiException(HttpStatus.BAD_GATEWAY, "Cổng thanh toán đang lỗi, vui lòng thử lại"));

    ApiException e = catchThrowableOfType(() -> service.reconcile(ORDER), ApiException.class);

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(payment.getStatus()).isEqualTo("PENDING");
    verify(results, never()).apply(anyLong(), any());
  }

  @Test
  @DisplayName("Link đã quá hạn mà PayOS vẫn PENDING: huỷ link và chuyển đơn EXPIRED")
  void expiredLink_cancels() {
    payment.setLinkExpiresAt(NOW.minus(Duration.ofMinutes(1)));
    when(gateway.getStatus(ORDER)).thenReturn(new PayOsGateway.OrderStatus("PENDING", 0));

    service.reconcile(ORDER);

    verify(gateway).cancel(eq(ORDER), anyString());
    verify(results).apply(ORDER, new GatewayResult("EXPIRED", 0, null));
  }

  @Test
  @DisplayName("Đơn chưa từng tạo được link: huỷ luôn, không gọi PayOS")
  void noLink_cancelsWithoutGateway() {
    payment.setCheckoutUrl(null);

    service.reconcile(ORDER);

    verify(gateway, never()).getStatus(anyLong());
    verify(results).apply(ORDER, new GatewayResult("CANCELLED", 0, null));
  }

  @Test
  @DisplayName("Đối tác huỷ đơn nhưng PayOS báo vừa trả xong: không huỷ nhầm")
  void cancelByPartner_paidMeanwhile_doesNotCancel() {
    when(gateway.getStatus(ORDER)).thenReturn(new PayOsGateway.OrderStatus("PAID", 100_000));
    // AdPaymentResultService (mock) đã ghi nhận thanh toán
    payment.setStatus("SUCCESS");

    service.cancelByPartner(ORDER);

    verify(gateway, never()).cancel(anyLong(), anyString());
    verify(results, never()).apply(ORDER, new GatewayResult("CANCELLED", 0, null));
  }

  @Test
  @DisplayName("Huỷ link trên PayOS lỗi vẫn huỷ đơn trên hệ thống")
  void cancelByPartner_gatewayCancelFails_stillCancels() {
    when(gateway.getStatus(ORDER)).thenReturn(new PayOsGateway.OrderStatus("PENDING", 0));
    doThrow(new ApiException(HttpStatus.BAD_GATEWAY, "x"))
        .when(gateway)
        .cancel(eq(ORDER), anyString());

    service.cancelByPartner(ORDER);

    verify(results).apply(ORDER, new GatewayResult("CANCELLED", 0, null));
  }
}
