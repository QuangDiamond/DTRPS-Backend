package com.dtrps.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sign-up step 1: name and email; a code is emailed to prove the address. */
public record RegisterOtpRequest(
    @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(min = 2, max = 150, message = "Họ tên phải có từ 2 đến 150 ký tự")
        String name,
    @NotBlank(message = "Vui lòng nhập email")
        @Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email quá dài")
        String email) {}
