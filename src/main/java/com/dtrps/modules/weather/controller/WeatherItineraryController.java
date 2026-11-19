package com.dtrps.modules.weather.controller;

import com.dtrps.modules.itinerary.dto.ItineraryItemDtos;
import com.dtrps.modules.weather.dto.WeatherDtos;
import com.dtrps.modules.weather.dto.WeatherImpactDtos;
import com.dtrps.modules.weather.service.WeatherImpactService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Weather for a specific itinerary + weather-impact warnings/apply (UC-045). Requires login and
 * ownership of the itinerary's plan — unlike GET /api/weather, these are not public.
 */
@RestController
@RequestMapping("/api/itineraries/{itineraryId}")
public class WeatherItineraryController {
  private final WeatherImpactService impactService;

  public WeatherItineraryController(WeatherImpactService impactService) {
    this.impactService = impactService;
  }

  @GetMapping("/weather")
  public WeatherDtos.ItineraryWeatherResponse weather(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long itineraryId) {
    return impactService.weatherForItinerary(CurrentUser.id(jwt), itineraryId);
  }

  @GetMapping("/weather-impact")
  public WeatherImpactDtos.ImpactResponse impact(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long itineraryId) {
    return impactService.evaluate(CurrentUser.id(jwt), itineraryId);
  }

  /** Chỉ áp dụng khi khách chọn một đề xuất — không tự động sửa lịch trình. */
  @PostMapping("/weather-impact/apply")
  public ItineraryItemDtos.ItemResponse apply(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long itineraryId,
      @Valid @RequestBody WeatherImpactDtos.ApplyRequest request) {
    return impactService.apply(CurrentUser.id(jwt), itineraryId, request);
  }
}
