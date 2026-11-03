package com.dtrps.modules.businessowner.controller;

import com.dtrps.modules.businessowner.dto.BusinessListingRequest;
import com.dtrps.modules.businessowner.dto.BusinessListingResponse;
import com.dtrps.modules.businessowner.dto.ListingRevisionResponse;
import com.dtrps.modules.businessowner.service.BusinessListingService;
import com.dtrps.modules.businessowner.service.ListingRevisionService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in business owner's places — a business may register several. Submitting needs an
 * ACTIVE business profile; another owner's place answers 404.
 */
@RestController
@RequestMapping("/api/business-listings")
public class BusinessListingController {
  private final BusinessListingService listingService;
  private final ListingRevisionService revisionService;

  public BusinessListingController(
      BusinessListingService listingService, ListingRevisionService revisionService) {
    this.listingService = listingService;
    this.revisionService = revisionService;
  }

  /** Newest first. */
  @GetMapping
  public List<BusinessListingResponse> list(@AuthenticationPrincipal Jwt jwt) {
    return listingService.listOwn(CurrentUser.id(jwt));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BusinessListingResponse submit(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BusinessListingRequest request) {
    return listingService.submit(CurrentUser.id(jwt), request);
  }

  @GetMapping("/{id}")
  public BusinessListingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return listingService.getOwn(CurrentUser.id(jwt), id);
  }

  /** Only while PENDING or REJECTED; an approved place changes through a revision. */
  @PutMapping("/{id}")
  public BusinessListingResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody BusinessListingRequest request) {
    return listingService.updateOwn(CurrentUser.id(jwt), id, request);
  }

  /** Proposes a change to an approved place; the public place stays as is until approved. */
  @PostMapping("/{id}/revisions")
  @ResponseStatus(HttpStatus.CREATED)
  public ListingRevisionResponse propose(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody BusinessListingRequest request) {
    return revisionService.propose(CurrentUser.id(jwt), id, request);
  }

  @GetMapping("/{id}/revisions")
  public List<ListingRevisionResponse> revisions(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return revisionService.listOwn(CurrentUser.id(jwt), id);
  }

  /** Withdraws a revision that is still waiting for review. */
  @PostMapping("/revisions/{revisionId}/cancel")
  public ListingRevisionResponse cancel(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long revisionId) {
    return revisionService.cancel(CurrentUser.id(jwt), revisionId);
  }
}
