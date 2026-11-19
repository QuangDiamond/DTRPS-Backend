package com.dtrps.modules.admin.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.dto.IntegrationDtos.IntegrationStatus;
import com.dtrps.modules.auth.config.GoogleProperties;
import com.dtrps.modules.discovery.config.OpenAiProperties;
import com.dtrps.modules.maps.config.GoongProperties;
import com.dtrps.modules.media.config.CloudinaryProperties;
import com.dtrps.modules.payment.config.PayOsProperties;
import com.dtrps.modules.weather.config.WeatherProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Health of the external services. Listing only reads configuration; a live check calls the service
 * with a harmless read (no payment, no upload, no email sent) and is limited to one per service
 * every {@link #CHECK_COOLDOWN} so the page cannot hammer them.
 */
@Service
public class IntegrationHealthService {
  private static final Logger log = LoggerFactory.getLogger(IntegrationHealthService.class);
  static final Duration CHECK_COOLDOWN = Duration.ofSeconds(10);

  static final String PAYOS = "PAYOS";
  static final String CLOUDINARY = "CLOUDINARY";
  static final String MAIL = "MAIL";
  static final String WEATHER = "WEATHER";
  static final String GOONG = "GOONG";
  static final String GOOGLE = "GOOGLE";
  static final String OPENAI = "OPENAI";

  /** Đơn không tồn tại: PayOS trả "not found" khi key đúng, lỗi xác thực khi key sai */
  private static final long PROBE_ORDER_CODE = 1L;

  private record CheckResult(
      String status, String message, long latencyMs, Instant at, Map<String, Object> details) {}

  private final PayOsProperties payOs;
  private final CloudinaryProperties cloudinary;
  private final WeatherProperties weather;
  private final GoongProperties goong;
  private final GoogleProperties google;
  private final OpenAiProperties openAi;
  private final JavaMailSender mailSender;
  private final SystemSettingsService settings;
  private final Clock clock;
  private final RestClient http;
  private final Map<String, CheckResult> lastChecks = new ConcurrentHashMap<>();

  public IntegrationHealthService(
      PayOsProperties payOs,
      CloudinaryProperties cloudinary,
      WeatherProperties weather,
      GoongProperties goong,
      GoogleProperties google,
      OpenAiProperties openAi,
      JavaMailSender mailSender,
      SystemSettingsService settings,
      Clock clock) {
    this.payOs = payOs;
    this.cloudinary = cloudinary;
    this.weather = weather;
    this.goong = goong;
    this.google = google;
    this.openAi = openAi;
    this.mailSender = mailSender;
    this.settings = settings;
    this.clock = clock;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(8));
    this.http = RestClient.builder().requestFactory(factory).build();
  }

  public List<IntegrationStatus> list() {
    List<IntegrationStatus> result = new ArrayList<>();
    result.add(status(PAYOS, "PayOS", "Thanh toán gói quảng cáo", payOs.configured(), true));
    result.add(
        status(
            CLOUDINARY,
            "Cloudinary",
            "Lưu ảnh địa điểm, đánh giá, ảnh đại diện",
            cloudinaryConfigured(),
            true));
    result.add(
        status(MAIL, "Email (SMTP)", "Gửi mã OTP đăng ký, quên mật khẩu", mailConfigured(), true));
    result.add(status(WEATHER, "Open-Meteo", "Dự báo thời tiết cho lịch trình", true, true));
    result.add(status(GOONG, "Goong Maps", "Tìm địa chỉ, chỉ đường", goong.configured(), false));
    result.add(
        status(GOOGLE, "Google Sign-In", "Đăng nhập bằng Google", google.configured(), false));
    result.add(status(OPENAI, "OpenAI", "Gợi ý địa điểm bằng AI", openAi.configured(), false));
    return result;
  }

  public IntegrationStatus check(String key) {
    IntegrationStatus current =
        list().stream()
            .filter(item -> item.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không có dịch vụ này"));
    if (!current.checkable()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Dịch vụ này chỉ kiểm tra được cấu hình");
    }
    if (!current.configured()) {
      throw new ApiException(HttpStatus.CONFLICT, "Dịch vụ chưa được cấu hình trên máy chủ");
    }
    CheckResult previous = lastChecks.get(key);
    Instant now = clock.instant();
    if (previous != null && previous.at().plus(CHECK_COOLDOWN).isAfter(now)) {
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS, "Vừa kiểm tra xong, vui lòng đợi vài giây rồi thử lại");
    }
    long started = System.nanoTime();
    CheckResult result;
    try {
      Map<String, Object> details =
          switch (key) {
            case PAYOS -> checkPayOs();
            case CLOUDINARY -> checkCloudinary();
            case MAIL -> checkMail();
            case WEATHER -> checkWeather();
            default -> throw new IllegalStateException(key);
          };
      result = new CheckResult("OK", "Kết nối bình thường", elapsed(started), now, details);
    } catch (CheckFailed e) {
      result = new CheckResult("ERROR", e.getMessage(), elapsed(started), now, Map.of());
    } catch (RuntimeException e) {
      log.warn("Integration check {} failed: {}", key, e.getClass().getSimpleName());
      result =
          new CheckResult(
              "ERROR",
              "Không kết nối được (hết thời gian chờ hoặc lỗi mạng)",
              elapsed(started),
              now,
              Map.of());
    }
    lastChecks.put(key, result);
    return list().stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
  }

  // ===== Checks =====

  private Map<String, Object> checkPayOs() {
    String base =
        payOs.baseUrl() == null || payOs.baseUrl().isBlank()
            ? "https://api-merchant.payos.vn"
            : payOs.baseUrl();
    try {
      JsonNode response =
          http.get()
              .uri(base + "/v2/payment-requests/{id}", PROBE_ORDER_CODE)
              .header("x-client-id", payOs.clientId())
              .header("x-api-key", payOs.apiKey())
              .retrieve()
              .body(JsonNode.class);
      String code = response == null ? "" : response.path("code").asText();
      // 00 = có đơn, 101 = không có đơn: cả hai nghĩa là key hợp lệ
      if (!"00".equals(code) && !"101".equals(code)) {
        throw new CheckFailed(
            "PayOS từ chối khoá API (mã " + code + "). Kiểm tra lại Client ID / API Key");
      }
    } catch (HttpClientErrorException e) {
      throw new CheckFailed("PayOS từ chối khoá API (HTTP " + e.getStatusCode().value() + ")");
    }
    return Map.of();
  }

  private Map<String, Object> checkCloudinary() {
    URI uri = URI.create(cloudinary.url().trim());
    String[] credentials =
        uri.getUserInfo() == null ? new String[0] : uri.getUserInfo().split(":", 2);
    if (credentials.length != 2 || uri.getHost() == null) {
      throw new CheckFailed("CLOUDINARY_URL sai định dạng (cloudinary://key:secret@cloud)");
    }
    String auth =
        Base64.getEncoder()
            .encodeToString(
                (decode(credentials[0]) + ":" + decode(credentials[1]))
                    .getBytes(StandardCharsets.UTF_8));
    JsonNode usage;
    try {
      usage =
          http.get()
              .uri("https://api.cloudinary.com/v1_1/{cloud}/usage", uri.getHost())
              .header("Authorization", "Basic " + auth)
              .retrieve()
              .body(JsonNode.class);
    } catch (HttpClientErrorException e) {
      throw new CheckFailed("Cloudinary từ chối khoá API (HTTP " + e.getStatusCode().value() + ")");
    }
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("cloud", uri.getHost());
    if (usage != null) {
      details.put("plan", usage.path("plan").asText(""));
      JsonNode credits = usage.path("credits");
      if (!credits.isMissingNode()) {
        details.put("creditsUsed", credits.path("usage").asDouble());
        details.put("creditsLimit", credits.path("limit").asDouble());
        details.put("creditsPercent", credits.path("used_percent").asDouble());
      }
      details.put("resources", usage.path("resources").asLong());
      details.put("storageBytes", usage.path("storage").path("usage").asLong());
    }
    return details;
  }

  private Map<String, Object> checkMail() {
    if (!(mailSender instanceof JavaMailSenderImpl impl)) {
      throw new CheckFailed("Không kiểm tra được cấu hình email");
    }
    try {
      impl.testConnection();
    } catch (jakarta.mail.MessagingException e) {
      throw new CheckFailed(
          "Máy chủ email từ chối đăng nhập. Kiểm tra MAIL_USERNAME / App Password");
    }
    return Map.of("host", impl.getHost() + ":" + impl.getPort());
  }

  private Map<String, Object> checkWeather() {
    String base =
        weather.baseUrl() == null || weather.baseUrl().isBlank()
            ? WeatherProperties.DEFAULT_BASE_URL
            : weather.baseUrl();
    try {
      JsonNode body =
          http.get()
              .uri(
                  base
                      + "/v1/forecast?latitude=21.03&longitude=105.85&daily=temperature_2m_max"
                      + "&forecast_days=1&timezone=auto")
              .retrieve()
              .body(JsonNode.class);
      if (body == null || body.path("daily").isMissingNode()) {
        throw new CheckFailed("Dịch vụ thời tiết trả dữ liệu không đúng định dạng");
      }
    } catch (HttpClientErrorException e) {
      throw new CheckFailed("Dịch vụ thời tiết trả lỗi HTTP " + e.getStatusCode().value());
    }
    return Map.of();
  }

  // ===== Helpers =====

  private IntegrationStatus status(
      String key, String name, String purpose, boolean configured, boolean checkable) {
    Map<String, Object> details = new LinkedHashMap<>();
    if (PAYOS.equals(key)) {
      Instant lastWebhook = settings.payOsLastWebhookAt();
      details.put("lastWebhookAt", lastWebhook == null ? null : lastWebhook.toString());
      details.put("jobsEnabled", payOs.jobsEnabled());
    }
    if (!configured) {
      return new IntegrationStatus(
          key,
          name,
          purpose,
          false,
          checkable,
          "NOT_CONFIGURED",
          "Chưa cấu hình trên máy chủ",
          null,
          null,
          details);
    }
    CheckResult last = lastChecks.get(key);
    if (last == null) {
      return new IntegrationStatus(
          key,
          name,
          purpose,
          true,
          checkable,
          // Dịch vụ không có kiểm tra kết nối: có khoá là coi như đã cấu hình xong
          checkable ? "UNCHECKED" : "CONFIGURED",
          checkable
              ? "Đã cấu hình, chưa kiểm tra kết nối"
              : "Đã có khoá trên máy chủ (kiểm tra khi dùng chức năng)",
          null,
          null,
          details);
    }
    details.putAll(last.details());
    return new IntegrationStatus(
        key,
        name,
        purpose,
        true,
        checkable,
        last.status(),
        last.message(),
        last.latencyMs(),
        last.at(),
        details);
  }

  private boolean cloudinaryConfigured() {
    return cloudinary.url() != null && !cloudinary.url().isBlank();
  }

  private boolean mailConfigured() {
    return mailSender instanceof JavaMailSenderImpl impl
        && impl.getUsername() != null
        && !impl.getUsername().isBlank()
        && impl.getPassword() != null
        && !impl.getPassword().isBlank();
  }

  private static long elapsed(long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  /** A failed check with a message safe to show the admin. */
  private static final class CheckFailed extends RuntimeException {
    CheckFailed(String message) {
      super(message);
    }
  }
}
