package com.dtrps.modules.auth.controller;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.auth.dto.GoogleSignInRequest;
import com.dtrps.modules.auth.dto.GoogleSignInResponse;
import com.dtrps.modules.auth.service.GoogleSignInService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Continue with Google" for travellers on the web and the mobile app (not for admin accounts). */
@RestController
@RequestMapping("/api/auth")
public class GoogleSignInController {
  static final String MISSING_TOKEN = "Thiếu thông tin đăng nhập Google";

  private final GoogleSignInService googleSignInService;

  public GoogleSignInController(GoogleSignInService googleSignInService) {
    this.googleSignInService = googleSignInService;
  }

  @PostMapping("/google")
  public GoogleSignInResponse signIn(@Valid @RequestBody GoogleSignInRequest request) {
    // App mobile gửi ID token; web gửi access token như trước
    if (request.idToken() != null && !request.idToken().isBlank()) {
      return googleSignInService.signInWithIdToken(request.idToken());
    }
    if (request.accessToken() == null || request.accessToken().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, MISSING_TOKEN);
    }
    return googleSignInService.signIn(request.accessToken());
  }
}
