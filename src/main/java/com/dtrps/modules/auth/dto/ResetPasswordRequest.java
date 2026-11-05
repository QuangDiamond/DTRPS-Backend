package com.dtrps.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Forgot password step 3: the ticket from the verified code and the new password. */
public record ResetPasswordRequest(
    @NotBlank(message = "Phiên xác thực không hợp lệ") @Size(max = 200) String ticket,
    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String newPassword) {}
