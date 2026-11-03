package com.dtrps.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        @Size(max = 256, message = "Mật khẩu quá dài")
        String currentPassword,
    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String newPassword) {}
