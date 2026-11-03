package com.dtrps.modules.user.controller;

import com.dtrps.modules.user.dto.PreferenceResponse;
import com.dtrps.modules.user.dto.UpdatePreferencesRequest;
import com.dtrps.modules.user.dto.UpdateProfileRequest;
import com.dtrps.modules.user.dto.UserResponse;
import com.dtrps.modules.user.service.PreferenceService;
import com.dtrps.modules.user.service.UserService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
  private final UserService userService;
  private final PreferenceService preferenceService;

  public UserController(UserService userService, PreferenceService preferenceService) {
    this.userService = userService;
    this.preferenceService = preferenceService;
  }

  @GetMapping
  public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    return userService.getProfile(CurrentUser.id(jwt));
  }

  @PutMapping
  public UserResponse updateProfile(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
    return userService.updateProfile(CurrentUser.id(jwt), request);
  }

  @GetMapping("/preferences")
  public PreferenceResponse preferences(@AuthenticationPrincipal Jwt jwt) {
    return preferenceService.get(CurrentUser.id(jwt));
  }

  @PutMapping("/preferences")
  public PreferenceResponse updatePreferences(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdatePreferencesRequest request) {
    return preferenceService.update(CurrentUser.id(jwt), request);
  }
}
