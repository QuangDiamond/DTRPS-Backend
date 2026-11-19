package com.dtrps.modules.weather.controller;

import com.dtrps.modules.weather.dto.WeatherDtos;
import com.dtrps.modules.weather.service.WeatherService;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public weather lookup for one place (GET /api/weather?placeId=&from=&to=). */
@RestController
@Validated
public class WeatherController {
  private final WeatherService weatherService;

  public WeatherController(WeatherService weatherService) {
    this.weatherService = weatherService;
  }

  @GetMapping("/api/weather")
  public WeatherDtos.WeatherResponse forecast(
      @RequestParam @NotNull Long placeId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return weatherService.forecast(placeId, from, to);
  }
}
