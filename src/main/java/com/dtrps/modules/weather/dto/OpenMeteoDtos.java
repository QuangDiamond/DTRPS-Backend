package com.dtrps.modules.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Raw response shape of Open-Meteo's /v1/forecast — snake_case fields, parallel arrays per key. */
public final class OpenMeteoDtos {
  private OpenMeteoDtos() {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ForecastResponse(Daily daily, Hourly hourly) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Daily(
      List<String> time,
      @JsonProperty("weather_code") List<Integer> weatherCode,
      @JsonProperty("temperature_2m_max") List<Double> temperatureMax,
      @JsonProperty("temperature_2m_min") List<Double> temperatureMin,
      @JsonProperty("precipitation_probability_max") List<Integer> precipitationProbabilityMax,
      @JsonProperty("precipitation_sum") List<Double> precipitationSum,
      @JsonProperty("wind_speed_10m_max") List<Double> windSpeedMax) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Hourly(
      List<String> time,
      @JsonProperty("weather_code") List<Integer> weatherCode,
      @JsonProperty("temperature_2m") List<Double> temperature,
      @JsonProperty("precipitation_probability") List<Integer> precipitationProbability,
      List<Double> precipitation,
      @JsonProperty("wind_speed_10m") List<Double> windSpeed) {}
}
