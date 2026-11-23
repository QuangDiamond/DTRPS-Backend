package com.dtrps.modules.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.repository.WeatherForecastRepository;
import com.dtrps.modules.itinerary.repository.WeatherSummaryRepository;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.weather.config.WeatherProperties;
import com.dtrps.modules.weather.dto.OpenMeteoDtos;
import com.dtrps.modules.weather.dto.WeatherDtos;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** GBR-49 (cache 1h), GBR-50 (stale fallback / 503), GBR-47 (ngoài 7 ngày → notAvailable). */
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {
  private static final Long PLACE_ID = 1L;
  private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final WeatherProperties PROPERTIES =
      new WeatherProperties(null, 60, 70, 7.6, List.of(65, 67, 82, 95, 96, 99), null);

  @Mock private PlaceRepository placeRepository;
  @Mock private WeatherForecastRepository forecastRepository;
  @Mock private WeatherSummaryRepository summaryRepository;
  @Mock private OpenMeteoGateway gateway;
  @Mock private JdbcTemplate jdbcTemplate;

  private WeatherService service;
  private Place place;

  @BeforeEach
  void setUp() {
    service =
        new WeatherService(
            placeRepository,
            forecastRepository,
            summaryRepository,
            gateway,
            PROPERTIES,
            CLOCK,
            jdbcTemplate);
    place = new Place();
    place.setPlaceId(PLACE_ID);
    place.setLatitude(21.0285);
    place.setLongitude(105.8542);
    when(placeRepository.findById(PLACE_ID)).thenReturn(Optional.of(place));
    // Not every test reaches these (e.g. the 503-with-no-cache case throws before querying rows).
    lenient()
        .when(
            forecastRepository
                .findByPlacePlaceIdAndForecastDateBetweenOrderByForecastDateAscForecastTimeAsc(
                    eq(PLACE_ID), any(), any()))
        .thenReturn(List.of());
    lenient()
        .when(summaryRepository.findDailySummaries(eq(PLACE_ID), any(), any()))
        .thenReturn(List.of());
  }

  @Test
  void freshCacheWithinTtlSkipsOpenMeteoCall() {
    when(forecastRepository.findLatestFetchedAt(PLACE_ID))
        .thenReturn(Optional.of(NOW.minusSeconds(60))); // 1 phút trước, TTL 60 phút

    WeatherDtos.WeatherResponse response = service.forecast(PLACE_ID, null, null);

    verify(gateway, never()).forecast(anyDouble(), anyDouble());
    assertThat(response.stale()).isFalse();
  }

  @Test
  void expiredCacheTriggersRefetchAndReplacesStoredRows() {
    when(forecastRepository.findLatestFetchedAt(PLACE_ID))
        .thenReturn(Optional.of(NOW.minusSeconds(61 * 60))); // 61 phút trước, hết hạn TTL 60 phút
    when(gateway.forecast(anyDouble(), anyDouble())).thenReturn(emptyResponse());

    WeatherDtos.WeatherResponse response = service.forecast(PLACE_ID, null, null);

    verify(gateway).forecast(place.getLatitude(), place.getLongitude());
    verify(forecastRepository).deleteByPlaceId(PLACE_ID);
    assertThat(response.stale()).isFalse();
  }

  @Test
  void gatewayFailureFallsBackToStaleCacheWhenSomeAlreadyExists() {
    when(forecastRepository.findLatestFetchedAt(PLACE_ID))
        .thenReturn(Optional.of(NOW.minusSeconds(61 * 60)));
    when(gateway.forecast(anyDouble(), anyDouble()))
        .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "unavailable"));

    WeatherDtos.WeatherResponse response = service.forecast(PLACE_ID, null, null);

    assertThat(response.stale()).isTrue(); // MSG13
    verify(forecastRepository, never()).deleteByPlaceId(PLACE_ID);
  }

  @Test
  void gatewayFailureWithNoCacheAtAllThrows503() {
    when(forecastRepository.findLatestFetchedAt(PLACE_ID)).thenReturn(Optional.empty());
    when(gateway.forecast(anyDouble(), anyDouble()))
        .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "unavailable"));

    assertThatThrownBy(() -> service.forecast(PLACE_ID, null, null))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)); // MSG42
  }

  @Test
  void dateBeyondSevenDayWindowGoesToNotAvailableInsteadOfErroring() {
    when(forecastRepository.findLatestFetchedAt(PLACE_ID))
        .thenReturn(Optional.of(NOW.minusSeconds(60)));

    LocalDate today = LocalDate.now(CLOCK);
    LocalDate farFuture = today.plusDays(10); // vượt ngoài today+6 (forecast_days=7)

    WeatherDtos.WeatherResponse response = service.forecast(PLACE_ID, today, farFuture);

    assertThat(response.notAvailable()).contains(farFuture);
    assertThat(response.notAvailable()).doesNotContain(today);
  }

  private static OpenMeteoDtos.ForecastResponse emptyResponse() {
    OpenMeteoDtos.Hourly hourly =
        new OpenMeteoDtos.Hourly(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    return new OpenMeteoDtos.ForecastResponse(null, hourly);
  }
}
