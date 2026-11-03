package com.dtrps.modules.itinerary.entity;

import com.dtrps.modules.poi.entity.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "weather_forecasts",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_weather_place_date",
          columnNames = {"place_id", "forecast_date"})
    })
@Check(
    name = "chk_precipitation_probability",
    constraints =
        "(((precipitation_probability IS NULL) OR ((precipitation_probability >= (0)::numeric) AND (precipitation_probability <= (100)::numeric))))")
@Check(
    name = "chk_weather_temperature",
    constraints =
        "(((temperature_min IS NULL) OR (temperature_max IS NULL) OR (temperature_min <= temperature_max)))")
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

  @Column(name = "temperature_min", precision = 5, scale = 2)
  private BigDecimal temperatureMin;

  @Column(name = "temperature_max", precision = 5, scale = 2)
  private BigDecimal temperatureMax;

  @Column(name = "weather_condition", length = 100)
  private String weatherCondition;

  @Column(name = "precipitation_probability", precision = 5, scale = 2)
  private BigDecimal precipitationProbability;

  @Column(name = "source", length = 100)
  private String source;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;
}
