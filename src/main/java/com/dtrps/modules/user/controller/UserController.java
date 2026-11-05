package com.dtrps.modules.user.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.media.service.ImageUploadService;
import com.dtrps.modules.poi.dto.PlaceSummaryResponse;
import com.dtrps.modules.poi.service.FavoriteService;
import com.dtrps.modules.user.dto.PreferenceResponse;
import com.dtrps.modules.user.dto.UpdatePreferencesRequest;
import com.dtrps.modules.user.dto.UpdateProfileRequest;
import com.dtrps.modules.user.dto.UserResponse;
import com.dtrps.modules.user.service.PreferenceService;
import com.dtrps.modules.user.service.UserService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/users/me")
public class UserController {
  private static final int MAX_PAGE_SIZE = 100;
  private final UserService userService;
  private final PreferenceService preferenceService;
  private final ImageUploadService imageUploadService;
  private final FavoriteService favoriteService;

  public UserController(
      UserService userService,
      PreferenceService preferenceService,
      ImageUploadService imageUploadService,
      FavoriteService favoriteService) {
    this.userService = userService;
    this.preferenceService = preferenceService;
    this.imageUploadService = imageUploadService;
    this.favoriteService = favoriteService;
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

  /**
   * Saved places. {@code sort} is {@code recent} (default, newest saved first) or {@code name}.
   * {@code group} filters on a service-group reference code.
   */
  @GetMapping("/favorites")
  public PageResponse<PlaceSummaryResponse> favorites(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String group,
      @RequestParam(defaultValue = "recent") String sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    Sort order =
        "name".equals(sort)
            ? Sort.by("place.name")
            : Sort.by(Sort.Direction.DESC, "createdAt");
    return favoriteService.list(
        CurrentUser.id(jwt), keyword, group, PageRequest.of(page, size, order));
  }

  /** Saving an already-saved place is not an error. */
  @PutMapping("/favorites/{placeId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void addFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long placeId) {
    favoriteService.add(CurrentUser.id(jwt), placeId);
  }

  /** Removing a place that is not saved is not an error. */
  @DeleteMapping("/favorites/{placeId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long placeId) {
    favoriteService.remove(CurrentUser.id(jwt), placeId);
  }
}
