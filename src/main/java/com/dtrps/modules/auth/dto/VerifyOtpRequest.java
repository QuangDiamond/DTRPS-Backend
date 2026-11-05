package com.dtrps.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Step 2: the 6-digit code from the email. */
public record VerifyOtpRequest(
    @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không hợp lệ") String email,
    @NotBlank(message = "Vui lòng nhập mã xác thực")
        @Pattern(regexp = "^\\d{6}$", message = "Mã xác thực gồm 6 chữ số")
        String code) {}
