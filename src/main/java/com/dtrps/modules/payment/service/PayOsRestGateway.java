package com.dtrps.modules.payment.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.payment.config.PayOsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class PayOsRestGateway implements PayOsGateway {
  private static final Logger log = LoggerFactory.getLogger(PayOsRestGateway.class);
  private static final String DEFAULT_BASE_URL = "https://api-merchant.payos.vn";
  private static final String SUCCESS_CODE = "00";

  static final String NOT_CONFIGURED = "Cổng thanh toán chưa được cấu hình";
  static final String UNAVAILABLE = "Cổng thanh toán đang lỗi, vui lòng thử lại";

  private final PayOsProperties properties;
  private final RestClient restClient;

  public PayOsRestGateway(PayOsProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(15));
    String baseUrl =
        properties.baseUrl() == null || properties.baseUrl().isBlank()
            ? DEFAULT_BASE_URL
            : properties.baseUrl();
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }

  @Override
  public PaymentLink createLink(
      long orderCode,
      long amount,
      String description,
      String returnUrl,
      String cancelUrl,
      long expiredAtEpochSeconds) {
    requireConfigured();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("orderCode", orderCode);
    body.put("amount", amount);
    body.put("description", description);
    body.put("cancelUrl", cancelUrl);
    body.put("returnUrl", returnUrl);
    body.put("expiredAt", expiredAtEpochSeconds);
    body.put(
        "signature",
        PayOsSignature.forPaymentRequest(
            amount, cancelUrl, description, orderCode, returnUrl, properties.checksumKey()));
    JsonNode data = post("/v2/payment-requests", body, "create");
    return new PaymentLink(data.path("paymentLinkId").asText(), data.path("checkoutUrl").asText());
  }

  @Override
  public OrderStatus getStatus(long orderCode) {
    requireConfigured();
    try {
      JsonNode response =
          restClient
              .get()
              .uri("/v2/payment-requests/{id}", orderCode)
              .header("x-client-id", properties.clientId())
              .header("x-api-key", properties.apiKey())
              .retrieve()
              .body(JsonNode.class);
      JsonNode data = requireSuccess(response, "status");
      return new OrderStatus(data.path("status").asText(), data.path("amountPaid").asLong());
    } catch (RestClientException e) {
      throw unavailable("status", e);
    }
  }

  @Override
  public void cancel(long orderCode, String reason) {
    requireConfigured();
    post("/v2/payment-requests/" + orderCode + "/cancel", Map.of("cancellationReason", reason), "cancel");
  }

  @Override
  public boolean verifyWebhook(JsonNode data, String signature) {
    requireConfigured();
    return PayOsSignature.verifyWebhook(data, signature, properties.checksumKey());
  }

  private JsonNode post(String path, Map<String, Object> body, String action) {
    try {
      JsonNode response =
          restClient
              .post()
              .uri(path)
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-client-id", properties.clientId())
              .header("x-api-key", properties.apiKey())
              .body(body)
              .retrieve()
              .body(JsonNode.class);
      return requireSuccess(response, action);
    } catch (RestClientException e) {
      throw unavailable(action, e);
    }
  }

  private static JsonNode requireSuccess(JsonNode response, String action) {
    if (response == null || !SUCCESS_CODE.equals(response.path("code").asText())) {
      // Chỉ ghi mã lỗi, không ghi nội dung request (có chữ ký)
      log.warn(
          "PayOS {} rejected: code={}",
          action,
          response == null ? "empty" : response.path("code").asText());
      throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
    }
    return response.path("data");
  }

  private static ApiException unavailable(String action, RestClientException e) {
    log.warn("PayOS {} failed: {}", action, e.getClass().getSimpleName());
    return new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, NOT_CONFIGURED);
    }
  }
}
