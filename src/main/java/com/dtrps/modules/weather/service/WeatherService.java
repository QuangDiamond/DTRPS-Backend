package com.dtrps.modules.weather.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.entity.WeatherForecast;
import com.dtrps.modules.itinerary.repository.WeatherForecastRepository;
import com.dtrps.modules.itinerary.repository.WeatherSummaryRepository;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.weather.config.WeatherProperties;
import com.dtrps.modules.weather.dto.OpenMeteoDtos;
import com.dtrps.modules.weather.dto.WeatherDtos;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GBR-47 (tối đa 7 ngày tới — ngày xa hơn trả về trong {@code notAvailable}, không lỗi), GBR-49
 * (cache 1 giờ theo {@code fetched_at}), GBR-50 (nguồn lỗi thì dùng cache cũ + cờ stale/MSG13;
 * không có cache nào thì 503/MSG42).
 */
@Service
public class WeatherService {
  private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
  private static final DateTimeFormatter OPEN_METEO_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  static final String PLACE_NOT_FOUND = "Không tìm thấy địa điểm";
  static final String NO_COORDINATES = "Địa điểm chưa có tọa độ để tra thời tiết";
  // MSG42: hoàn toàn chưa có dữ liệu thời tiết nào (cache rỗng) và gọi nguồn cũng thất bại
  static final String NO_DATA_MESSAGE =
      "Chưa có dữ liệu thời tiết cho địa điểm này, vui lòng thử lại sau";

  private final PlaceRepository placeRepository;
  private final WeatherForecastRepository forecastRepository;
  private final WeatherSummaryRepository summaryRepository;
  private final OpenMeteoGateway gateway;
  private final WeatherProperties properties;
  private final Clock clock;

  public WeatherService(
      PlaceRepository placeRepository,
      WeatherForecastRepository forecastRepository,
      WeatherSummaryRepository summaryRepository,
      OpenMeteoGateway gateway,
      WeatherProperties properties,
      Clock clock) {
    this.placeRepository = placeRepository;
    this.forecastRepository = forecastRepository;
    this.summaryRepository = summaryRepository;
    this.gateway = gateway;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public WeatherDtos.WeatherResponse forecast(Long placeId, LocalDate from, LocalDate to) {
    Place place = requirePlace(placeId);
    return forecast(place, from, to);
  }

  @Transactional
  public WeatherDtos.WeatherResponse forecast(Place place, LocalDate from, LocalDate to) {
    if (place.getLatitude() == null || place.getLongitude() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, NO_COORDINATES);
    }

    LocalDate today = LocalDate.now(clock);
    LocalDate maxDate = today.plusDays(6); // forecast_days=7 => today..today+6
    LocalDate rangeFrom = from == null ? today : from;
    LocalDate rangeTo = to == null ? maxDate : to;

    List<LocalDate> notAvailable = new ArrayList<>();
    LocalDate clampedTo = rangeTo;
    if (rangeTo.isAfter(maxDate)) {
      clampedTo = maxDate;
      for (LocalDate d = maxDate.plusDays(1); !d.isAfter(rangeTo); d = d.plusDays(1)) {
        notAvailable.add(d);
      }
    }

    boolean stale = ensureFresh(place);

    List<WeatherDtos.HourlyForecast> hourly;
    List<WeatherDtos.DailyForecast> daily;
    if (clampedTo.isBefore(rangeFrom)) {
      hourly = List.of();
      daily = List.of();
    } else {
      hourly =
          forecastRepository
              .findByPlacePlaceIdAndForecastDateBetweenOrderByForecastDateAscForecastTimeAsc(
                  place.getPlaceId(), rangeFrom, clampedTo)
              .stream()
              .map(WeatherService::toHourly)
              .toList();
      daily =
          summaryRepository.findDailySummaries(place.getPlaceId(), rangeFrom, clampedTo).stream()
              .map(WeatherService::toDaily)
              .toList();
    }

    Instant fetchedAt = forecastRepository.findLatestFetchedAt(place.getPlaceId()).orElse(null);
    return new WeatherDtos.WeatherResponse(
        place.getPlaceId(), daily, hourly, notAvailable, stale, fetchedAt);
  }

  /** Raw hourly rows for a date range — used by WeatherImpactService to match activity windows. */
  @Transactional(readOnly = true)
  public List<WeatherForecast> hourlyBetween(Long placeId, LocalDate from, LocalDate to) {
    return forecastRepository
        .findByPlacePlaceIdAndForecastDateBetweenOrderByForecastDateAscForecastTimeAsc(
            placeId, from, to);
  }

  Place requirePlace(Long placeId) {
    return placeRepository
        .findById(placeId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PLACE_NOT_FOUND));
  }

  /** Refresh the place's forecast when due. Returns true if serving stale data (GBR-50/MSG13). */
  private boolean ensureFresh(Place place) {
    Optional<Instant> latest = forecastRepository.findLatestFetchedAt(place.getPlaceId());
    Instant now = clock.instant();
    boolean expired =
        latest.isEmpty()
            || latest
                .get()
                .isBefore(now.minusSeconds(properties.cacheTtlMinutesOrDefault() * 60L));

    if (!expired) {
      return false;
    }

    try {
      refetch(place);
      return false;
    } catch (ApiException gatewayFailure) {
      if (latest.isPresent()) {
        log.warn(
            "Open-Meteo refresh failed for place {}, serving stale cache", place.getPlaceId());
        return true;
      }
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, NO_DATA_MESSAGE);
    }
  }

  private void refetch(Place place) {
    OpenMeteoDtos.ForecastResponse response =
        gateway.forecast(place.getLatitude(), place.getLongitude());
    List<WeatherForecast> rows = toRows(place, response);
    forecastRepository.deleteByPlaceId(place.getPlaceId());
    forecastRepository.flush();
    forecastRepository.saveAll(rows);
  }

  private static List<WeatherForecast> toRows(Place place, OpenMeteoDtos.ForecastResponse response) {
    List<WeatherForecast> rows = new ArrayList<>();
    if (response == null || response.hourly() == null || response.hourly().time() == null) {
      return rows;
    }
    OpenMeteoDtos.Hourly hourly = response.hourly();
    for (int i = 0; i < hourly.time().size(); i++) {
      LocalDateTime dateTime = LocalDateTime.parse(hourly.time().get(i), OPEN_METEO_TIME);
      WeatherForecast row = new WeatherForecast();
      row.setPlace(place);
      row.setForecastDate(dateTime.toLocalDate());
      row.setForecastTime(dateTime.toLocalTime());
      row.setTemperature(at(hourly.temperature(), i));
      row.setWeatherCode(atInt(hourly.weatherCode(), i));
      row.setPrecipitationProbability(atInt(hourly.precipitationProbability(), i));
      row.setPrecipitation(at(hourly.precipitation(), i));
      row.setWindSpeed(at(hourly.windSpeed(), i));
      row.setSource("open-meteo");
      rows.add(row);
    }
    return rows;
  }

  private static BigDecimal at(List<Double> values, int index) {
    if (values == null || index >= values.size() || values.get(index) == null) {
      return null;
    }
    return BigDecimal.valueOf(values.get(index));
  }

  private static Integer atInt(List<Integer> values, int index) {
    if (values == null || index >= values.size()) {
      return null;
    }
    return values.get(index);
  }

  private static WeatherDtos.HourlyForecast toHourly(WeatherForecast row) {
    return new WeatherDtos.HourlyForecast(
        row.getForecastDate(),
        row.getForecastTime(),
        row.getTemperature(),
        row.getWeatherCode(),
        WeatherCodeMapper.label(row.getWeatherCode()),
        row.getPrecipitationProbability(),
        row.getPrecipitation(),
        row.getWindSpeed());
  }

  private static WeatherDtos.DailyForecast toDaily(WeatherSummaryRepository.DailySummary summary) {
    return new WeatherDtos.DailyForecast(
        summary.forecastDate(),
        summary.temperatureMax(),
        summary.temperatureMin(),
        summary.precipitationProbabilityMax(),
        summary.precipitationSum(),
        summary.windSpeedMax(),
        summary.weatherCode(),
        WeatherCodeMapper.label(summary.weatherCode()));
  }
}
