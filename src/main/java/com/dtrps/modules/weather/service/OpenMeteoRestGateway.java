package com.dtrps.modules.weather.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.weather.config.WeatherProperties;
import com.dtrps.modules.weather.dto.OpenMeteoDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** No API key needed — unlike GoongRestGateway there is nothing sensitive to keep out of logs. */
@Service
public class OpenMeteoRestGateway implements OpenMeteoGateway {
  private static final Logger log = LoggerFactory.getLogger(OpenMeteoRestGateway.class);
  static final String UNAVAILABLE = "Không gọi được dịch vụ thời tiết, vui lòng thử lại sau";

  private static final String DAILY_FIELDS =
      "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,"
          + "precipitation_sum,wind_speed_10m_max";
  private static final String HOURLY_FIELDS =
      "weather_code,temperature_2m,precipitation_probability,precipitation,wind_speed_10m";

  private final RestClient restClient;

  /**
   * Takes the auto-configured {@code RestClient.Builder} bean (Spring Boot provides one) rather
   * than calling {@code RestClient.builder()} directly, so tests can hand in a builder already
   * bound to {@code MockRestServiceServer} instead of hitting the real network. Deliberately does
   * NOT call {@code .requestFactory(...)} here (unlike GoongRestGateway, which builds its own
   * RestClient from scratch and sets a 5s/10s timeout factory): doing so on an injected builder
   * would silently overwrite whatever request factory {@code MockRestServiceServer.bindTo(...)}
   * had already installed on it, breaking the mock. Connect/read timeouts for this client are
   * whatever Spring Boot's RestClient auto-configuration defaults to (or `spring.http.client.*`
   * if the app ever sets those) — a deliberate testability-over-timeout-tuning tradeoff.
   */
  public OpenMeteoRestGateway(RestClient.Builder builder, WeatherProperties properties) {
    String baseUrl =
        properties.baseUrl() == null || properties.baseUrl().isBlank()
            ? WeatherProperties.DEFAULT_BASE_URL
            : properties.baseUrl();
    this.restClient = builder.baseUrl(baseUrl).build();
  }

  @Override
  public OpenMeteoDtos.ForecastResponse forecast(double latitude, double longitude) {
    try {
      return restClient
          .get()
          .uri(
              builder ->
                  builder
                      .path("/v1/forecast")
                      .queryParam("latitude", latitude)
                      .queryParam("longitude", longitude)
                      .queryParam("timezone", "Asia/Bangkok")
                      .queryParam("forecast_days", 7)
                      .queryParam("daily", DAILY_FIELDS)
                      .queryParam("hourly", HOURLY_FIELDS)
                      .build())
          .retrieve()
          .body(OpenMeteoDtos.ForecastResponse.class);
    } catch (RestClientException e) {
      log.warn("Open-Meteo request failed: {}", e.getClass().getSimpleName());
      throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
    }
  }
}
