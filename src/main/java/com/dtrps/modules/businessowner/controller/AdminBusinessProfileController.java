package com.dtrps.modules.businessowner.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.businessowner.dto.BusinessProfileResponse;
import com.dtrps.modules.businessowner.dto.BusinessProfileStatsResponse;
import com.dtrps.modules.businessowner.dto.RejectBusinessProfileRequest;
import com.dtrps.modules.businessowner.dto.UpdateBusinessStatusRequest;
import com.dtrps.modules.businessowner.service.BusinessProfileService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/business-profiles")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBusinessProfileController {
  private static final int MAX_PAGE_SIZE = 100;

  private final BusinessProfileService profileService;

  public AdminBusinessProfileController(BusinessProfileService profileService) {
    this.profileService = profileService;
  }

  /** Newest first. {@code status}: PENDING, ACTIVE, SUSPENDED or INACTIVE. */
  @GetMapping
  public PageResponse<BusinessProfileResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return profileService.list(
        status, keyword, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "businessId")));
  }

  @GetMapping("/stats")
  public BusinessProfileStatsResponse stats() {
    return profileService.stats();
  }

  @GetMapping("/{id}")
  public BusinessProfileResponse get(@PathVariable Long id) {
    return profileService.get(id);
  }

  @PostMapping("/{id}/approve")
  public BusinessProfileResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return profileService.approve(CurrentUser.id(jwt), id);
  }

  @PostMapping("/{id}/reject")
  public BusinessProfileResponse reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody RejectBusinessProfileRequest request) {
    return profileService.reject(CurrentUser.id(jwt), id, request.reason());
  }

  @PutMapping("/{id}/status")
  public BusinessProfileResponse updateStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody UpdateBusinessStatusRequest request) {
    return profileService.updateStatus(CurrentUser.id(jwt), id, request.status(), request.reason());
  }
}
