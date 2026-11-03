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

/** The signed-in business owner's single place. Needs an ACTIVE business profile to submit. */
@RestController
@RequestMapping("/api/business-listing")
public class BusinessListingController {
  private final BusinessListingService listingService;
  private final ListingRevisionService revisionService;

  public BusinessListingController(
      BusinessListingService listingService, ListingRevisionService revisionService) {
    this.listingService = listingService;
    this.revisionService = revisionService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BusinessListingResponse submit(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BusinessListingRequest request) {
    return listingService.submit(CurrentUser.id(jwt), request);
  }

  @GetMapping
  public BusinessListingResponse get(@AuthenticationPrincipal Jwt jwt) {
    return listingService.getOwn(CurrentUser.id(jwt));
  }

  @PutMapping
  public BusinessListingResponse update(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BusinessListingRequest request) {
    return listingService.updateOwn(CurrentUser.id(jwt), request);
  }

  /** Proposes a change to the approved place; the public place stays as is until approved. */
  @PostMapping("/revisions")
  @ResponseStatus(HttpStatus.CREATED)
  public ListingRevisionResponse propose(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BusinessListingRequest request) {
    return revisionService.propose(CurrentUser.id(jwt), request);
  }

  @GetMapping("/revisions")
  public List<ListingRevisionResponse> revisions(@AuthenticationPrincipal Jwt jwt) {
    return revisionService.listOwn(CurrentUser.id(jwt));
  }

  /** Withdraws a revision that is still waiting for review. */
  @PostMapping("/revisions/{id}/cancel")
  public ListingRevisionResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return revisionService.cancel(CurrentUser.id(jwt), id);
  }
}
