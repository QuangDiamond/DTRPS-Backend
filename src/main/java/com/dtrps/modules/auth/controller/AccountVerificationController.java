package com.dtrps.modules.auth.controller;

import com.dtrps.modules.auth.dto.AuthResponse;
import com.dtrps.modules.auth.dto.CompleteRegistrationRequest;
import com.dtrps.modules.auth.dto.EmailRequest;
import com.dtrps.modules.auth.dto.OtpSentResponse;
import com.dtrps.modules.auth.dto.OtpTicketResponse;
import com.dtrps.modules.auth.dto.RegisterOtpRequest;
import com.dtrps.modules.auth.dto.ResetPasswordRequest;
import com.dtrps.modules.auth.dto.VerifyOtpRequest;
import com.dtrps.modules.auth.service.AccountVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sign-up and password reset with an emailed 6-digit code (public, rate limited). */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Email verification", description = "Đăng ký và quên mật khẩu bằng mã OTP qua email")
public class AccountVerificationController {
  private final AccountVerificationService verification;

  public AccountVerificationController(AccountVerificationService verification) {
    this.verification = verification;
  }

  @PostMapping("/register/otp")
  @Operation(summary = "Đăng ký bước 1: gửi mã xác thực tới email")
  public OtpSentResponse sendRegisterCode(@Valid @RequestBody RegisterOtpRequest request) {
    return verification.sendRegisterCode(request);
  }

  @PostMapping("/register/verify")
  @Operation(summary = "Đăng ký bước 2: kiểm tra mã, trả vé dùng một lần")
  public OtpTicketResponse verifyRegisterCode(@Valid @RequestBody VerifyOtpRequest request) {
    return verification.verifyRegisterCode(request);
  }

  @PostMapping("/register/complete")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Đăng ký bước 3: đặt mật khẩu, tạo tài khoản và đăng nhập")
  public AuthResponse completeRegistration(
      @Valid @RequestBody CompleteRegistrationRequest request) {
    return verification.completeRegistration(request);
  }

  @PostMapping("/password/otp")
  @Operation(summary = "Quên mật khẩu bước 1: gửi mã tới email (nếu có tài khoản)")
  public OtpSentResponse sendResetCode(@Valid @RequestBody EmailRequest request) {
    return verification.sendResetCode(request);
  }

  @PostMapping("/password/verify")
  @Operation(summary = "Quên mật khẩu bước 2: kiểm tra mã, trả vé dùng một lần")
  public OtpTicketResponse verifyResetCode(@Valid @RequestBody VerifyOtpRequest request) {
    return verification.verifyResetCode(request);
  }

  @PostMapping("/password/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Quên mật khẩu bước 3: đặt mật khẩu mới (đăng xuất mọi phiên)")
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    verification.resetPassword(request);
  }
}
