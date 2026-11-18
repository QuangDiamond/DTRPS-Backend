package com.dtrps.modules.payment.controller;

import com.dtrps.modules.payment.dto.AdDtos;
import com.dtrps.modules.payment.service.FeaturedPlaceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Địa điểm được tài trợ ở trang chủ du khách — công khai. */
@RestController
@Validated
public class FeaturedPlaceController {
  private final FeaturedPlaceService featuredPlaces;

  public FeaturedPlaceController(FeaturedPlaceService featuredPlaces) {
    this.featuredPlaces = featuredPlaces;
  }

  @GetMapping("/api/featured-places")
  public List<AdDtos.FeaturedPlace> featured(
      @RequestParam(defaultValue = "8") @Min(1) @Max(24) int limit) {
    return featuredPlaces.featured(limit);
  }
}
