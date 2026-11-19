package com.dtrps.modules.itinerary.entity;

import com.dtrps.modules.poi.entity.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One row per (place, date, hour) — hourly granularity only. Daily summaries (GBR-47 display,
 * GBR-49 cache check) are derived from this via the {@code weather_daily_summary} view
 * (post-schema.sql), not stored separately, so the two can never drift apart.
 *
 * <p>Refreshing a place's forecast deletes all its rows and re-inserts a fresh batch (see
 * WeatherService) rather than updating in place, so {@code fetchedAt} is reliably "when this
 * place's cache was last refreshed" for every row.
 */
@Entity
@Table(
    name = "weather_forecasts",
    indexes = {@Index(name = "idx_weather_forecasts_place", columnList = "place_id")},
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_weather_place_date_time",
          columnNames = {"place_id", "forecast_date", "forecast_time"})
    })
@Check(
    name = "chk_weather_precipitation_probability",
    constraints =
        "(((precipitation_probability IS NULL) OR ((precipitation_probability >= 0) AND"
            + " (precipitation_probability <= 100))))")
@Getter
@Setter
public class WeatherForecast {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "forecast_id")
  private Long forecastId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "place_id", nullable = false)
  private Place place;

  @Column(name = "forecast_date", nullable = false)
  private LocalDate forecastDate;

  @Column(name = "forecast_time", nullable = false)
  private LocalTime forecastTime;

  @Column(name = "temperature", precision = 5, scale = 2)
  private BigDecimal temperature;

  /** WMO weather code from Open-Meteo (0 = clear ... 99 = severe thunderstorm). */
  @Column(name = "weather_code")
  private Integer weatherCode;

  @Column(name = "precipitation_probability")
  private Integer precipitationProbability;

  @Column(name = "precipitation", precision = 6, scale = 2)
  private BigDecimal precipitation;

  @Column(name = "wind_speed", precision = 6, scale = 2)
  private BigDecimal windSpeed;

  @ColumnDefault("'open-meteo'")
  @Column(name = "source", length = 50)
  private String source = "open-meteo";

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;
}
