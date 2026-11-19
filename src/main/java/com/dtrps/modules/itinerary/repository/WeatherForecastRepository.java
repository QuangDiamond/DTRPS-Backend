package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.WeatherForecast;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, Long> {
  List<WeatherForecast>
      findByPlacePlaceIdAndForecastDateBetweenOrderByForecastDateAscForecastTimeAsc(
          Long placeId, LocalDate from, LocalDate to);

  /** When this place's cache was last refreshed; empty if never fetched (GBR-49 TTL check). */
  @Query("SELECT MAX(w.fetchedAt) FROM WeatherForecast w WHERE w.place.placeId = :placeId")
  Optional<Instant> findLatestFetchedAt(@Param("placeId") Long placeId);

  @Modifying
  @Query("DELETE FROM WeatherForecast w WHERE w.place.placeId = :placeId")
  void deleteByPlaceId(@Param("placeId") Long placeId);
}
