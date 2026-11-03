package com.dtrps.modules.poi.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.poi.dto.AdminPlaceDetail;
import com.dtrps.modules.poi.dto.AdminPlaceStats;
import com.dtrps.modules.poi.dto.AdminPlaceSummary;
import com.dtrps.modules.poi.service.AdminPlaceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Every place in the system (admin POIs and partner places), newest update first. */
@RestController
@Validated
@RequestMapping("/api/admin/places")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlaceController {
  private static final int MAX_PAGE_SIZE = 100;

  private final AdminPlaceService placeService;

  public AdminPlaceController(AdminPlaceService placeService) {
    this.placeService = placeService;
  }

  @GetMapping
  public PageResponse<AdminPlaceSummary> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String group,
      @RequestParam(required = false) String province,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String source,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return placeService.list(
        keyword,
        group,
        province,
        status,
        source,
        PageRequest.of(
            page, size, Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("placeId"))));
  }

  @GetMapping("/stats")
  public AdminPlaceStats stats() {
    return placeService.stats();
  }

  @GetMapping("/{id}")
  public AdminPlaceDetail get(@PathVariable Long id) {
    return placeService.get(id);
  }
}
