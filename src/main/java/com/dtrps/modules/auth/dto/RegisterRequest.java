package com.dtrps.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(min = 2, max = 150, message = "Họ tên phải có từ 2 đến 150 ký tự")
        String name,
    @NotBlank(message = "Vui lòng nhập email")
        @Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email quá dài")
        String email,
    @NotBlank(message = "Vui lòng nhập mật khẩu")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password) {}
