package com.dtrps.modules.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WeatherCodeMapperTest {

  @Test
  void mapsRepresentativeCodesAcrossEveryRange() {
    assertThat(WeatherCodeMapper.label(0)).isEqualTo("Trời quang");
    assertThat(WeatherCodeMapper.label(2)).isEqualTo("Nhiều mây");
    assertThat(WeatherCodeMapper.label(45)).isEqualTo("Sương mù");
    assertThat(WeatherCodeMapper.label(55)).isEqualTo("Mưa phùn");
    assertThat(WeatherCodeMapper.label(63)).isEqualTo("Mưa");
    assertThat(WeatherCodeMapper.label(67)).isEqualTo("Mưa đóng băng");
    assertThat(WeatherCodeMapper.label(75)).isEqualTo("Tuyết");
    assertThat(WeatherCodeMapper.label(81)).isEqualTo("Mưa rào");
    assertThat(WeatherCodeMapper.label(86)).isEqualTo("Mưa tuyết rào");
    assertThat(WeatherCodeMapper.label(96)).isEqualTo("Giông bão");
  }

  @Test
  void unknownOrNullCodeFallsBackToUnknownLabel() {
    assertThat(WeatherCodeMapper.label(null)).isEqualTo("Không rõ");
    assertThat(WeatherCodeMapper.label(999)).isEqualTo("Không rõ");
  }
}
