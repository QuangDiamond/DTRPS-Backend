package com.dtrps.modules.weather.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.repository.ItineraryItemRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly refresh for places belonging to an itinerary that is ACTIVE and whose plan is either
 * IN_PROGRESS or starts within the next 7 days. Gated by {@code app.weather.jobs-enabled}, same
 * reason as {@code AdLifecycleJobs}: developers share one database, so an ungated job firing on
 * every local run would call Open-Meteo and overwrite the cache redundantly on every machine.
 */
@Component
@ConditionalOnProperty(name = "app.weather.jobs-enabled", havingValue = "true")
public class WeatherRefreshJob {
  private static final Logger log = LoggerFactory.getLogger(WeatherRefreshJob.class);
  private static final long ONE_HOUR = 3_600_000;
  private static final int SCOPE_DAYS_AHEAD = 7;

  private final ItineraryItemRepository itemRepository;
  private final WeatherService weatherService;
  private final Clock clock;

  public WeatherRefreshJob(
      ItineraryItemRepository itemRepository, WeatherService weatherService, Clock clock) {
    this.itemRepository = itemRepository;
    this.weatherService = weatherService;
    this.clock = clock;
  }

  @Scheduled(fixedRate = ONE_HOUR)
  void refresh() {
    LocalDate today = LocalDate.now(clock);
    LocalDate limit = today.plusDays(SCOPE_DAYS_AHEAD);
    List<Long> placeIds = itemRepository.findPlaceIdsForWeatherRefresh(today, limit);
    for (Long placeId : placeIds) {
      try {
        weatherService.forecast(placeId, null, null);
      } catch (ApiException e) {
        log.warn("Weather refresh failed for place {}: {}", placeId, e.getMessage());
      }
    }
  }
}
