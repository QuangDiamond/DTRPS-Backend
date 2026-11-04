package com.dtrps.modules.maps.controller;

import com.dtrps.modules.maps.dto.AddressSuggestion;
import com.dtrps.modules.maps.dto.GeoLocation;
import com.dtrps.modules.maps.dto.RouteResponse;
import com.dtrps.modules.maps.service.MapService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Goong map services for signed-in users (place forms, later trip directions). The Goong API key
 * stays on the server; only signed-in users can spend its quota.
 */
@RestController
@Validated
@RequestMapping("/api/maps")
public class MapController {
  private final MapService mapService;

  public MapController(MapService mapService) {
    this.mapService = mapService;
  }

  /** {@code lat}/{@code lng}: optional, ranks results near that point first. */
  @GetMapping("/autocomplete")
  public List<AddressSuggestion> autocomplete(
      @RequestParam
          @NotBlank(message = "Vui lòng nhập địa chỉ cần tìm")
          @Size(min = 2, max = 200, message = "Từ khóa tìm kiếm từ 2 đến 200 ký tự")
          String input,
      @RequestParam(required = false) @DecimalMin("-90") @DecimalMax("90") Double lat,
      @RequestParam(required = false) @DecimalMin("-180") @DecimalMax("180") Double lng) {
    return mapService.autocomplete(input, lat, lng);
  }

  @GetMapping("/places/{placeId}")
  public GeoLocation place(@PathVariable @Size(max = 500) String placeId) {
    return mapService.place(placeId);
  }

  @GetMapping("/reverse")
  public GeoLocation reverse(
      @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
      @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng) {
    return mapService.reverse(lat, lng);
  }

  /** {@code vehicle}: car (default), bike, taxi, truck or hd. */
  @GetMapping("/directions")
  public RouteResponse directions(
      @RequestParam @DecimalMin("-90") @DecimalMax("90") double fromLat,
      @RequestParam @DecimalMin("-180") @DecimalMax("180") double fromLng,
      @RequestParam @DecimalMin("-90") @DecimalMax("90") double toLat,
      @RequestParam @DecimalMin("-180") @DecimalMax("180") double toLng,
      @RequestParam(defaultValue = "car")
          @Pattern(
              regexp = "^(car|bike|taxi|truck|hd)$",
              message = "Phương tiện phải là car, bike, taxi, truck hoặc hd")
          String vehicle) {
    return mapService.directions(fromLat, fromLng, toLat, toLng, vehicle);
  }
}
