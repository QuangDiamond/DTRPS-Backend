package com.dtrps.modules.businessowner.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.businessowner.dto.BusinessListingResponse;
import com.dtrps.modules.businessowner.dto.BusinessListingStatsResponse;
import com.dtrps.modules.businessowner.dto.RejectListingRequest;
import com.dtrps.modules.businessowner.service.BusinessListingService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/business-listings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBusinessListingController {
  private static final int MAX_PAGE_SIZE = 100;

  private final BusinessListingService listingService;

  public AdminBusinessListingController(BusinessListingService listingService) {
    this.listingService = listingService;
  }

  /** Newest first. {@code status}: PENDING, APPROVED, REJECTED or SUSPENDED. */
  @GetMapping
  public PageResponse<BusinessListingResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return listingService.list(
        status, keyword, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "listingId")));
  }

  @GetMapping("/stats")
  public BusinessListingStatsResponse stats() {
    return listingService.stats();
  }

  @GetMapping("/{id}")
  public BusinessListingResponse get(@PathVariable Long id) {
    return listingService.get(id);
  }

  @PostMapping("/{id}/approve")
  public BusinessListingResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return listingService.approve(CurrentUser.id(jwt), id);
  }

  @PostMapping("/{id}/reject")
  public BusinessListingResponse reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody RejectListingRequest request) {
    return listingService.reject(CurrentUser.id(jwt), id, request.reason());
  }
}
