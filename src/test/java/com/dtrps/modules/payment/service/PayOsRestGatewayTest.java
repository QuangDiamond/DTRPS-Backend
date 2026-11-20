package com.dtrps.modules.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.payment.config.PayOsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit test PayOsRestGateway — sheet Payment, UTC065–UTC070 trong Report5.1 Unit Test. PayOS được
 * giả bằng HTTP server cục bộ, không gọi mạng thật.
 */
class PayOsRestGatewayTest {
  private static final String CHECKSUM = "test-checksum-key";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HttpServer server;
  private final AtomicReference<String> responseBody = new AtomicReference<>();
  private final AtomicReference<String> requestBody = new AtomicReference<>();
  private final AtomicReference<String> clientIdHeader = new AtomicReference<>();

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          clientIdHeader.set(exchange.getRequestHeaders().getFirst("x-client-id"));
          byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private PayOsRestGateway gateway(String checksumKey) {
    String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    return new PayOsRestGateway(
        new PayOsProperties(
            "client-id",
            "api-key",
            checksumKey,
            baseUrl,
            "http://localhost:5173",
            Duration.ofMinutes(15),
            false));
  }

  private static String hmac(String text) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(CHECKSUM.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  @Nested
  @DisplayName("createLink")
  class CreateLink {
    @Test
    @DisplayName("UTC065 PayOS trả code 00: trả checkoutUrl, request có chữ ký HMAC đúng")
    void success_returnsLink() throws Exception {
      responseBody.set(
          "{\"code\":\"00\",\"data\":{\"paymentLinkId\":\"pl-1\","
              + "\"checkoutUrl\":\"https://pay.payos.vn/web/abc\"}}");

      PayOsGateway.PaymentLink link =
          gateway(CHECKSUM)
              .createLink(100_010, 100_000, "ST100010", "http://r", "http://c", 1_800_000_000L);

      assertThat(link.paymentLinkId()).isEqualTo("pl-1");
      assertThat(link.checkoutUrl()).isEqualTo("https://pay.payos.vn/web/abc");
      assertThat(clientIdHeader.get()).isEqualTo("client-id");
      JsonNode sent = MAPPER.readTree(requestBody.get());
      assertThat(sent.path("amount").asLong()).isEqualTo(100_000);
      assertThat(sent.path("signature").asText())
          .isEqualTo(
              hmac(
                  "amount=100000&cancelUrl=http://c&description=ST100010"
                      + "&orderCode=100010&returnUrl=http://r"));
    }

    @Test
    @DisplayName("UTC066 Thiếu checksum key → 503")
    void notConfigured_unavailable() {
      ApiException e =
          error(() -> gateway("").createLink(1, 1_000, "ST1", "http://r", "http://c", 0));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
      assertThat(e.getMessage()).isEqualTo("Cổng thanh toán chưa được cấu hình");
    }

    @Test
    @DisplayName("UTC067 PayOS trả code khác 00 → 502")
    void rejected_badGateway() {
      responseBody.set("{\"code\":\"231\",\"desc\":\"Đơn thanh toán đã tồn tại\"}");

      ApiException e =
          error(() -> gateway(CHECKSUM).createLink(1, 1_000, "ST1", "http://r", "http://c", 0));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
      assertThat(e.getMessage()).isEqualTo("Cổng thanh toán đang lỗi, vui lòng thử lại");
    }
  }

  @Nested
  @DisplayName("verifyWebhook")
  class VerifyWebhook {
    private ObjectNode data() {
      ObjectNode data = MAPPER.createObjectNode();
      data.put("orderCode", 100_010);
      data.put("amount", 100_000);
      data.put("code", "00");
      data.put("description", "ST100010");
      return data;
    }

    /** Ký độc lập với code: sắp key theo bảng chữ cái, nối key=value bằng & */
    private String sign() throws Exception {
      return hmac("amount=100000&code=00&description=ST100010&orderCode=100010");
    }

    @Test
    @DisplayName("UTC068 Chữ ký đúng → true")
    void validSignature_true() throws Exception {
      assertThat(gateway(CHECKSUM).verifyWebhook(data(), sign())).isTrue();
    }

    @Test
    @DisplayName("UTC069 Sửa số tiền trong data → false")
    void tamperedAmount_false() throws Exception {
      ObjectNode tampered = data();
      tampered.put("amount", 1_000);

      assertThat(gateway(CHECKSUM).verifyWebhook(tampered, sign())).isFalse();
    }

    @Test
    @DisplayName("UTC070 Thiếu checksum key → 503")
    void notConfigured_unavailable() throws Exception {
      String signature = sign();

      ApiException e = error(() -> gateway(null).verifyWebhook(data(), signature));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
  }
}
