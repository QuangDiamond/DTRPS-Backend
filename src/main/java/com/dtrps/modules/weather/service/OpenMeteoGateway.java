package com.dtrps.modules.weather.service;

import com.dtrps.modules.weather.dto.OpenMeteoDtos;

/** Raw access to the Open-Meteo forecast API; tests replace it. */
public interface OpenMeteoGateway {
  /** 7-day daily + hourly forecast for a coordinate (Asia/Bangkok timezone). */
  OpenMeteoDtos.ForecastResponse forecast(double latitude, double longitude);
}
