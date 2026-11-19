package com.dtrps.modules.payment.controller;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.SystemSettingsService;
import com.dtrps.modules.payment.service.AdPaymentResultService;
import com.dtrps.modules.payment.service.PayOsGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** PayOS báo kết quả thanh toán. Công khai, nên chữ ký phải được kiểm tra trước mọi xử lý. */
@RestController
public class PayOsWebhookController {
  private static final String PAID_CODE = "00";

  private final PayOsGateway gateway;
  private final AdPaymentResultService results;

  private final SystemSettingsService settings;

  public PayOsWebhookController(
      PayOsGateway gateway, AdPaymentResultService results, SystemSettingsService settings) {
    this.gateway = gateway;
    this.results = results;
    this.settings = settings;
  }

  @PostMapping("/api/payments/payos/webhook")
  public Map<String, Boolean> receive(@RequestBody JsonNode body) {
    JsonNode data = body.path("data");
    if (!gateway.verifyWebhook(data, body.path("signature").asText(null))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Chữ ký không hợp lệ");
    }
    // Trang Cài đặt hệ thống hiện lần cuối PayOS gọi về (để biết webhook đã đăng ký đúng)
    settings.markPayOsWebhook();
    if (PAID_CODE.equals(data.path("code").asText())) {
      results.apply(
          data.path("orderCode").asLong(),
          new AdPaymentResultService.GatewayResult(
              "PAID", data.path("amount").asLong(), data.path("reference").asText(null)));
    }
    return Map.of("success", true);
  }
}
