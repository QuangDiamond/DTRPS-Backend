package com.dtrps.modules.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.dto.IntegrationDtos.IntegrationStatus;
import com.dtrps.modules.auth.config.GoogleProperties;
import com.dtrps.modules.discovery.config.OpenAiProperties;
import com.dtrps.modules.maps.config.GoongProperties;
import com.dtrps.modules.media.config.CloudinaryProperties;
import com.dtrps.modules.payment.config.PayOsProperties;
import com.dtrps.modules.weather.config.WeatherProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Unit test IntegrationHealthService.check — sheet Admin, UTC005–UTC009 trong Report5.1. PayOS được
 * giả bằng HTTP server cục bộ, không gọi mạng thật.
 */
@ExtendWith(MockitoExtension.class)
class IntegrationHealthServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");

  @Mock private SystemSettingsService settings;

  private HttpServer payOs;
  private final AtomicInteger payOsCalls = new AtomicInteger();
  private final MovableClock clock = new MovableClock(NOW);

  /** Đồng hồ chỉnh được để thử thời gian chờ giữa hai lần kiểm tra */
  private static final class MovableClock extends Clock {
    private Instant now;

    MovableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @BeforeEach
  void startPayOs() throws Exception {
    payOs = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    payOs.createContext(
        "/",
        exchange -> {
          payOsCalls.incrementAndGet();
          // 101 = không có đơn: khoá đúng
          byte[] body =
              "{\"code\":\"101\",\"desc\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    payOs.start();
  }

  @AfterEach
  void stopPayOs() {
    payOs.stop(0);
  }

  private IntegrationHealthService service(String cloudinaryUrl, String openAiKey) {
    String base = "http://127.0.0.1:" + payOs.getAddress().getPort();
    return new IntegrationHealthService(
        new PayOsProperties(
            "client", "key", "checksum", base, "http://localhost:5173", null, false),
        new CloudinaryProperties(cloudinaryUrl, null, null),
        new WeatherProperties(null, null, null, null, null, null),
        new GoongProperties(null, null),
        new GoogleProperties(null),
        new OpenAiProperties(openAiKey, null, null),
        new JavaMailSenderImpl(),
        settings,
        clock);
  }

  private static ApiException error(IntegrationHealthService service, String key) {
    return catchThrowableOfType(() -> service.check(key), ApiException.class);
  }

  @Test
  @DisplayName("UTC005 PayOS configured and answering → status OK with latency, no key in details")
  void payOs_ok() {
    IntegrationStatus status = service(null, null).check("PAYOS");

    assertThat(status.status()).isEqualTo("OK");
    assertThat(status.message()).isEqualTo("Kết nối bình thường");
    assertThat(status.latencyMs()).isNotNull();
    assertThat(status.checkedAt()).isEqualTo(NOW);
    assertThat(status.details().toString()).doesNotContain("key", "checksum");
    assertThat(payOsCalls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("UTC006 Unknown service key → 404")
  void unknownKey_notFound() {
    ApiException e = error(service(null, null), "ABC");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(e.getMessage()).isEqualTo("Không có dịch vụ này");
  }

  @Test
  @DisplayName("UTC007 CLOUDINARY_URL not set → 409")
  void cloudinaryMissing_conflict() {
    ApiException e = error(service(null, null), "CLOUDINARY");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(e.getMessage()).isEqualTo("Dịch vụ chưa được cấu hình trên máy chủ");
  }

  @Test
  @DisplayName("UTC008 OpenAI only supports a configuration check → 400")
  void openAi_notCheckable() {
    ApiException e = error(service(null, "sk-test"), "OPENAI");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).isEqualTo("Dịch vụ này chỉ kiểm tra được cấu hình");
  }

  @Test
  @DisplayName("UTC009 Checked 3 seconds ago → 429; allowed again after 10 seconds")
  void cooldown() {
    IntegrationHealthService service = service(null, null);
    service.check("PAYOS");
    clock.advance(Duration.ofSeconds(3));

    ApiException e = error(service, "PAYOS");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(e.getMessage()).isEqualTo("Vừa kiểm tra xong, vui lòng đợi vài giây rồi thử lại");
    assertThat(payOsCalls.get()).isEqualTo(1);

    clock.advance(Duration.ofSeconds(7));
    assertThat(service.check("PAYOS").status()).isEqualTo("OK");
    assertThat(payOsCalls.get()).isEqualTo(2);
  }
}
