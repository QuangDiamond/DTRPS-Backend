package com.dtrps.modules.auth.controller;

import com.dtrps.modules.auth.dto.GoogleSignInRequest;
import com.dtrps.modules.auth.dto.GoogleSignInResponse;
import com.dtrps.modules.auth.service.GoogleSignInService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Continue with Google" on the web (not for admin accounts). */
@RestController
@RequestMapping("/api/auth")
public class GoogleSignInController {
  private final GoogleSignInService googleSignInService;

  public GoogleSignInController(GoogleSignInService googleSignInService) {
    this.googleSignInService = googleSignInService;
  }

  @PostMapping("/google")
  public GoogleSignInResponse signIn(@Valid @RequestBody GoogleSignInRequest request) {
    return googleSignInService.signIn(request.accessToken());
  }
}
