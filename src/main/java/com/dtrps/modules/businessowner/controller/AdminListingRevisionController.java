package com.dtrps.modules.businessowner.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.businessowner.dto.ApproveRevisionRequest;
import com.dtrps.modules.businessowner.dto.ListingRevisionResponse;
import com.dtrps.modules.businessowner.dto.RejectRevisionRequest;
import com.dtrps.modules.businessowner.dto.RevisionReviewDetail;
import com.dtrps.modules.businessowner.service.ListingRevisionService;
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

/** Review queue for changes to approved places. */
@RestController
@RequestMapping("/api/admin/listing-revisions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminListingRevisionController {
  private static final int MAX_PAGE_SIZE = 100;

  private final ListingRevisionService revisionService;

  public AdminListingRevisionController(ListingRevisionService revisionService) {
    this.revisionService = revisionService;
  }

  /** Newest first. {@code status}: PENDING, APPROVED, REJECTED or CANCELLED. */
  @GetMapping
  public PageResponse<ListingRevisionResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return revisionService.list(
        status, keyword, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "revisionId")));
  }

  /** The proposed content next to the place as it is public now. */
  @GetMapping("/{id}")
  public RevisionReviewDetail get(@PathVariable Long id) {
    return revisionService.get(id);
  }

  @PostMapping("/{id}/approve")
  public RevisionReviewDetail approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody(required = false) ApproveRevisionRequest request) {
    return revisionService.approve(
        CurrentUser.id(jwt), id, request == null ? null : request.note());
  }

  @PostMapping("/{id}/reject")
  public ListingRevisionResponse reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody RejectRevisionRequest request) {
    return revisionService.reject(CurrentUser.id(jwt), id, request);
  }
}
