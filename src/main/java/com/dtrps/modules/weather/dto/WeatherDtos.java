package com.dtrps.modules.weather.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class WeatherDtos {
  private WeatherDtos() {}

  public record DailyForecast(
      LocalDate date,
      BigDecimal temperatureMax,
      BigDecimal temperatureMin,
      Integer precipitationProbabilityMax,
      BigDecimal precipitationSum,
      BigDecimal windSpeedMax,
      Integer weatherCode,
      String condition) {}

  public record HourlyForecast(
      LocalDate date,
      LocalTime time,
      BigDecimal temperature,
      Integer weatherCode,
      String condition,
      Integer precipitationProbability,
      BigDecimal precipitation,
      BigDecimal windSpeed) {}

  /**
   * {@code notAvailable}: requested dates beyond the 7-day Open-Meteo window (GBR-47) — client
   * shows "Forecast not yet available" for those instead of an error. {@code stale}: true when
   * the live Open-Meteo call just failed and this is old cached data (GBR-50, MSG13).
   */
  public record WeatherResponse(
      Long placeId,
      List<DailyForecast> daily,
      List<HourlyForecast> hourly,
      List<LocalDate> notAvailable,
      boolean stale,
      Instant fetchedAt) {}

  /** One place's forecast within an itinerary's overview (GET /api/itineraries/{id}/weather). */
  public record ItineraryWeatherResponse(Long itineraryId, List<WeatherResponse> places) {}
}
