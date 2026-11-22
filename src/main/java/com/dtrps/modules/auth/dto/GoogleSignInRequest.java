package com.dtrps.modules.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * Đăng nhập Google: web gửi {@code accessToken} (cửa sổ đăng nhập Google), app mobile gửi
 * {@code idToken} (Google Sign-In SDK). Cần một trong hai.
 */
public record GoogleSignInRequest(
    @Size(max = 4096, message = "Thông tin đăng nhập Google không hợp lệ") String accessToken,
    @Size(max = 4096, message = "Thông tin đăng nhập Google không hợp lệ") String idToken) {}
