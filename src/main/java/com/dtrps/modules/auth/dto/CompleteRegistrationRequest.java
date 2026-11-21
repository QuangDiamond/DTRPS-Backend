package com.dtrps.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sign-up step 3: the ticket from the verified code, the name and the password. */
public record CompleteRegistrationRequest(
    @NotBlank(message = "Phiên xác thực không hợp lệ") @Size(max = 200) String ticket,
    @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(min = 2, max = 150, message = "Họ tên phải có từ 2 đến 150 ký tự")
        String name,
    @NotBlank(message = "Vui lòng nhập mật khẩu")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password,
    /** "TRAVELLER" (mặc định) hoặc "BUSINESS_OWNER" — xác định loại tài khoản tạo ra */
    @Pattern(
            regexp = "TRAVELLER|BUSINESS_OWNER",
            message = "accountType phải là TRAVELLER hoặc BUSINESS_OWNER")
        String accountType) {

  /** Đăng ký du khách (giữ chữ ký cũ cho code/test đang dùng). */
  public CompleteRegistrationRequest(String ticket, String name, String password) {
    this(ticket, name, password, null);
  }
}
