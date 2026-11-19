package com.dtrps.modules.weather.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Open-Meteo forecast API (no key required) and the "heavy rain / storm" thresholds used by the
 * weather-impact feature (UC-045, GBR-48). All fields have sane defaults so the feature works out
 * of the box; override via application.yaml / env vars to tune without a redeploy.
 */
@ConfigurationProperties("app.weather")
public record WeatherProperties(
    String baseUrl,
    Integer cacheTtlMinutes,
    Integer heavyRainProbability,
    Double heavyRainMmPerHour,
    List<Integer> stormWeatherCodes,
    Boolean jobsEnabled) {
  public static final String DEFAULT_BASE_URL = "https://api.open-meteo.com";

  public int cacheTtlMinutesOrDefault() {
    return cacheTtlMinutes == null ? 60 : cacheTtlMinutes;
  }

  public int heavyRainProbabilityOrDefault() {
    return heavyRainProbability == null ? 70 : heavyRainProbability;
  }

  public double heavyRainMmPerHourOrDefault() {
    return heavyRainMmPerHour == null ? 7.6 : heavyRainMmPerHour;
  }

  public List<Integer> stormWeatherCodesOrDefault() {
    return stormWeatherCodes == null || stormWeatherCodes.isEmpty()
        ? List.of(65, 67, 82, 95, 96, 99)
        : stormWeatherCodes;
  }

  public boolean jobsEnabledOrDefault() {
    return Boolean.TRUE.equals(jobsEnabled);
  }
}
