package com.dtrps.modules.itinerary.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the {@code weather_daily_summary} view (derived from weather_forecasts), JDBC-based. */
@Repository
public class WeatherSummaryRepository {

  private final JdbcTemplate jdbcTemplate;

  public WeatherSummaryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // Dữ liệu trả về từ view weather_daily_summary
  public record DailySummary(
      LocalDate forecastDate,
      BigDecimal temperatureMax,
      BigDecimal temperatureMin,
      Integer precipitationProbabilityMax,
      BigDecimal precipitationSum,
      BigDecimal windSpeedMax,
      Integer weatherCode,
      Instant fetchedAt) {}

  public List<DailySummary> findDailySummaries(Long placeId, LocalDate from, LocalDate to) {
    String sql =
        """
        SELECT forecast_date, temperature_max, temperature_min, precipitation_probability_max,
               precipitation_sum, wind_speed_max, weather_code, fetched_at
        FROM weather_daily_summary
        WHERE place_id = ? AND forecast_date BETWEEN ? AND ?
        ORDER BY forecast_date ASC
        """;
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) ->
            new DailySummary(
                rs.getObject("forecast_date", LocalDate.class),
                rs.getBigDecimal("temperature_max"),
                rs.getBigDecimal("temperature_min"),
                (Integer) rs.getObject("precipitation_probability_max"),
                rs.getBigDecimal("precipitation_sum"),
                rs.getBigDecimal("wind_speed_max"),
                (Integer) rs.getObject("weather_code"),
                rs.getTimestamp("fetched_at").toInstant()),
        placeId,
        from,
        to);
  }
}
