package com.dtrps.modules.businessowner.controller;

import com.dtrps.modules.businessowner.dto.BusinessProfileRequest;
import com.dtrps.modules.businessowner.dto.BusinessProfileResponse;
import com.dtrps.modules.businessowner.service.BusinessProfileService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own business profile. Any logged-in user may register one. */
@RestController
@RequestMapping("/api/business-profile")
public class BusinessProfileController {
  private final BusinessProfileService profileService;

  public BusinessProfileController(BusinessProfileService profileService) {
    this.profileService = profileService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BusinessProfileResponse register(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BusinessProfileRequest request) {
    return profileService.register(CurrentUser.id(jwt), request);
  }

  @GetMapping
  public BusinessProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
    return profileService.getOwn(CurrentUser.id(jwt));
  }

  @PutMapping
  public BusinessProfileResponse update(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BusinessProfileRequest request) {
    return profileService.updateOwn(CurrentUser.id(jwt), request);
  }
}
