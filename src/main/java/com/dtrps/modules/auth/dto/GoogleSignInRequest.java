package com.dtrps.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Access token from the Google sign-in popup on the web. */
public record GoogleSignInRequest(
    @NotBlank(message = "Thiếu thông tin đăng nhập Google")
        @Size(max = 4096, message = "Thông tin đăng nhập Google không hợp lệ")
        String accessToken) {}
