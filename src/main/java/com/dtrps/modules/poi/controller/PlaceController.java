package com.dtrps.modules.poi.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.poi.dto.PlaceDetailResponse;
import com.dtrps.modules.poi.dto.PlaceSummaryResponse;
import com.dtrps.modules.poi.service.PlaceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public place discovery for travellers: the ACTIVE places list and detail. */
@RestController
@Validated
@RequestMapping("/api/places")
public class PlaceController {
  private static final int MAX_PAGE_SIZE = 100;

  private final PlaceService placeService;

  public PlaceController(PlaceService placeService) {
    this.placeService = placeService;
  }

  @GetMapping
  public PageResponse<PlaceSummaryResponse> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String province,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return placeService.list(
        keyword,
        category,
        province,
        PageRequest.of(
            page, size, Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("placeId"))));
  }

  @GetMapping("/{id}")
  public PlaceDetailResponse get(@PathVariable Long id) {
    return placeService.get(id);
  }
}
