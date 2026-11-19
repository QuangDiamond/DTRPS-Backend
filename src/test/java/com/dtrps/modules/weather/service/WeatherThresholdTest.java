package com.dtrps.modules.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dtrps.modules.weather.config.WeatherProperties;
import com.dtrps.modules.weather.dto.WeatherDtos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** GBR-48: xác suất mưa cao + lượng mưa lớn, HOẶC mã thời tiết thuộc danh sách giông/bão. */
class WeatherThresholdTest {
  private static final WeatherProperties PROPERTIES =
      new WeatherProperties(null, null, 70, 7.6, List.of(65, 67, 82, 95, 96, 99), null);

  private static WeatherDtos.HourlyForecast hour(
      Integer weatherCode, Integer probability, Double precipitation) {
    return new WeatherDtos.HourlyForecast(
        LocalDate.of(2026, 11, 19),
        LocalTime.of(14, 0),
        BigDecimal.valueOf(28),
        weatherCode,
        WeatherCodeMapper.label(weatherCode),
        probability,
        precipitation == null ? null : BigDecimal.valueOf(precipitation),
        BigDecimal.valueOf(10));
  }

  @Test
  void heavyRainAtOrAboveBothThresholdsIsBad() {
    assertThat(WeatherImpactService.isBadWeather(hour(61, 70, 7.6), PROPERTIES)).isTrue();
    assertThat(WeatherImpactService.isBadWeather(hour(61, 90, 15.0), PROPERTIES)).isTrue();
  }

  @Test
  void belowEitherRainThresholdIsNotBadOnRainAlone() {
    assertThat(WeatherImpactService.isBadWeather(hour(61, 69, 7.6), PROPERTIES)).isFalse();
    assertThat(WeatherImpactService.isBadWeather(hour(61, 70, 7.5), PROPERTIES)).isFalse();
  }

  @Test
  void stormCodeIsBadRegardlessOfRainFields() {
    assertThat(WeatherImpactService.isBadWeather(hour(95, null, null), PROPERTIES)).isTrue();
    assertThat(WeatherImpactService.isBadWeather(hour(99, 0, 0.0), PROPERTIES)).isTrue();
  }

  @Test
  void clearOrCloudyWeatherIsNotBad() {
    assertThat(WeatherImpactService.isBadWeather(hour(0, 5, 0.0), PROPERTIES)).isFalse();
    assertThat(WeatherImpactService.isBadWeather(hour(2, null, null), PROPERTIES)).isFalse();
  }
}
