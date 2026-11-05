package com.dtrps.modules.auth.controller;

import com.dtrps.modules.auth.dto.AuthResponse;
import com.dtrps.modules.auth.dto.ChangePasswordRequest;
import com.dtrps.modules.auth.dto.LoginRequest;
import com.dtrps.modules.auth.dto.RefreshRequest;
import com.dtrps.modules.auth.dto.RegisterRequest;
import com.dtrps.modules.auth.service.AuthService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @PostMapping("/refresh")
  public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return authService.refresh(request.refreshToken());
  }

  @PostMapping("/revoke")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@Valid @RequestBody RefreshRequest request) {
    authService.revoke(request.refreshToken());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@AuthenticationPrincipal Jwt jwt) {
    authService.logout(CurrentUser.id(jwt));
  }

  /** Other devices are signed out; this one gets a new session in the response. */
  @PostMapping("/password/change")
  public AuthResponse changePassword(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
    return authService.changePassword(CurrentUser.id(jwt), request);
  }
}
