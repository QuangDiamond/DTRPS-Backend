package com.dtrps.modules.auth.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.auth.dto.AuthResponse;
import com.dtrps.modules.auth.dto.CompleteRegistrationRequest;
import com.dtrps.modules.auth.dto.EmailRequest;
import com.dtrps.modules.auth.dto.OtpSentResponse;
import com.dtrps.modules.auth.dto.OtpTicketResponse;
import com.dtrps.modules.auth.dto.RegisterOtpRequest;
import com.dtrps.modules.auth.dto.ResetPasswordRequest;
import com.dtrps.modules.auth.dto.VerifyOtpRequest;
import com.dtrps.modules.auth.entity.EmailOtp;
import com.dtrps.modules.user.repository.UserRepository;
import com.dtrps.modules.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-up and password reset proved by an emailed code: email → code → finish (create the account,
 * or set the new password).
 */
@Service
public class AccountVerificationService {
  private static final String EMAIL_TAKEN = "Email này đã có tài khoản — hãy đăng nhập";

  private final OtpService otpService;
  private final AuthService authService;
  private final UserRepository userRepository;

  public AccountVerificationService(
      OtpService otpService, AuthService authService, UserRepository userRepository) {
    this.otpService = otpService;
    this.authService = authService;
    this.userRepository = userRepository;
  }

  // ===== Sign-up =====

  @Transactional
  public OtpSentResponse sendRegisterCode(RegisterOtpRequest request) {
    String email = UserService.normalizeEmail(request.email());
    if (userRepository.existsByEmail(email)) {
      throw new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN);
    }
    return otpService.send(email, request.name().trim(), EmailOtp.REGISTER, true);
  }

  public OtpTicketResponse verifyRegisterCode(VerifyOtpRequest request) {
    String email = UserService.normalizeEmail(request.email());
    return new OtpTicketResponse(
        otpService.verify(email, request.code(), EmailOtp.REGISTER), otpService.ticketSeconds());
  }

  @Transactional
  public AuthResponse completeRegistration(CompleteRegistrationRequest request) {
    String email = otpService.consume(request.ticket(), EmailOtp.REGISTER);
    return authService.registerVerified(request.name().trim(), email, request.password());
  }

  // ===== Password reset =====

  /**
   * Answers the same whether or not the email has an account, so the form does not reveal who is
   * registered; the code is only sent to active accounts.
   */
  @Transactional
  public OtpSentResponse sendResetCode(EmailRequest request) {
    String email = UserService.normalizeEmail(request.email());
    var user = userRepository.findByEmail(email);
    boolean deliver = user.map(found -> "ACTIVE".equals(found.getStatus())).orElse(false);
    return otpService.send(
        email,
        user.map(found -> found.getFullName()).orElse(null),
        EmailOtp.RESET_PASSWORD,
        deliver);
  }

  public OtpTicketResponse verifyResetCode(VerifyOtpRequest request) {
    String email = UserService.normalizeEmail(request.email());
    return new OtpTicketResponse(
        otpService.verify(email, request.code(), EmailOtp.RESET_PASSWORD),
        otpService.ticketSeconds());
  }

  @Transactional
  public void resetPassword(ResetPasswordRequest request) {
    String email = otpService.consume(request.ticket(), EmailOtp.RESET_PASSWORD);
    authService.resetPassword(email, request.newPassword());
  }
}
