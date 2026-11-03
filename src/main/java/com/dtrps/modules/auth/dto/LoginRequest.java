package com.dtrps.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "Vui lòng nhập email")
        @Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email quá dài")
        String email,
    @NotBlank(message = "Vui lòng nhập mật khẩu") @Size(max = 256, message = "Mật khẩu quá dài")
        String password) {}
