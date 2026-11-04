package com.dtrps.modules.user.controller;

import com.dtrps.modules.media.service.ImageUploadService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
  private final UserService userService;
  private final PreferenceService preferenceService;
  private final ImageUploadService imageUploadService;

  public UserController(
      UserService userService,
      PreferenceService preferenceService,
      ImageUploadService imageUploadService) {
    this.userService = userService;
    this.preferenceService = preferenceService;
    this.imageUploadService = imageUploadService;
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

  /**
   * Any signed-in user (traveller, partner, admin) changes their profile picture: jpg / png / webp
   * up to 5MB, saved right away. Returns the updated profile.
   */
  @PostMapping("/avatar")
  public UserResponse uploadAvatar(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
    String url = imageUploadService.uploadAvatar(file);
    return userService.updateAvatar(CurrentUser.id(jwt), url);
  }

  @DeleteMapping("/avatar")
  public UserResponse removeAvatar(@AuthenticationPrincipal Jwt jwt) {
    return userService.updateAvatar(CurrentUser.id(jwt), null);
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
