package com.dtrps.modules.weather.service;

/** WMO weather codes (used by Open-Meteo) to a short Vietnamese condition label. */
public final class WeatherCodeMapper {
  private WeatherCodeMapper() {}

  public static String label(Integer code) {
    if (code == null) {
      return "Không rõ";
    }
    return switch (code) {
      case 0 -> "Trời quang";
      case 1, 2, 3 -> "Nhiều mây";
      case 45, 48 -> "Sương mù";
      case 51, 53, 55, 56, 57 -> "Mưa phùn";
      case 61, 63, 65 -> "Mưa";
      case 66, 67 -> "Mưa đóng băng";
      case 71, 73, 75, 77 -> "Tuyết";
      case 80, 81, 82 -> "Mưa rào";
      case 85, 86 -> "Mưa tuyết rào";
      case 95, 96, 99 -> "Giông bão";
      default -> "Không rõ";
    };
  }
}
