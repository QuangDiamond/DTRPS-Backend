package com.dtrps.modules.auth.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.auth.dto.AuthResponse;
import com.dtrps.modules.auth.dto.ChangePasswordRequest;
import com.dtrps.modules.auth.dto.LoginRequest;
import com.dtrps.modules.auth.dto.RegisterRequest;
import com.dtrps.modules.auth.dto.RotatedSession;
import com.dtrps.modules.user.entity.RoleName;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import com.dtrps.modules.user.service.UserService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private static final int BCRYPT_MAX_BYTES = 72;
  private static final String AUDIT_ENTITY = "User";
  private static final String EMAIL_TAKEN = "Email đã được sử dụng";
  private static final String PASSWORD_TOO_LONG = "Mật khẩu quá dài";
  private static final String INVALID_CREDENTIALS = "Email hoặc mật khẩu không chính xác";
  private static final String ACCOUNT_LOCKED = "Tài khoản đã bị khóa";
  private static final String USER_NOT_FOUND = "Phiên đăng nhập không hợp lệ";
  private static final String WRONG_CURRENT_PASSWORD = "Mật khẩu hiện tại không chính xác";
  private static final String TOO_MANY_FAILED_LOGINS =
      "Bạn đã nhập sai quá nhiều lần, vui lòng thử lại sau";

  private final UserRepository userRepository;
  private final UserService userService;
  private final TokenService tokenService;
  private final LoginThrottle loginThrottle;
  private final AuditService auditService;
  private final PasswordEncoder passwordEncoder;
  private final String decoyPasswordHash;

  public AuthService(
      UserRepository userRepository,
      UserService userService,
      TokenService tokenService,
      LoginThrottle loginThrottle,
      AuditService auditService,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.userService = userService;
    this.tokenService = tokenService;
    this.loginThrottle = loginThrottle;
    this.auditService = auditService;
    this.passwordEncoder = passwordEncoder;
    this.decoyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    requireBcryptLength(request.password());
    String email = UserService.normalizeEmail(request.email());
    if (userRepository.existsByEmail(email)) {
      throw new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN);
    }
    User user = createTraveller(request, email);
    return AuthResponse.of(user, tokenService.issue(user));
  }

  @Transactional
  public AuthResponse login(LoginRequest request) {
    String email = UserService.normalizeEmail(request.email());
    if (loginThrottle.isBlocked(email)) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_FAILED_LOGINS);
    }
    User user = userRepository.findByEmail(email).orElse(null);
    if (!passwordMatches(request.password(), user)) {
      loginThrottle.recordFailure(email);
      throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
    }
    loginThrottle.reset(email);
    if (!TokenService.USER_ACTIVE.equals(user.getStatus())) {
      throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_LOCKED);
    }
    return AuthResponse.of(user, tokenService.issue(user));
  }

  /** Commits on rejection so that revoking a replayed token family is not rolled back. */
  @Transactional(noRollbackFor = ApiException.class)
  public AuthResponse refresh(String refreshToken) {
    RotatedSession session = tokenService.rotate(refreshToken);
    return AuthResponse.of(session.user(), session.tokens());
  }

  @Transactional
  public void logout(Long userId) {
    tokenService.revokeAll(userId);
  }

  /** Holding the refresh token is the proof, so this works after the access token expires. */
  @Transactional
  public void revoke(String refreshToken) {
    tokenService.revoke(refreshToken);
  }

  /** Ends every session, so a stolen refresh token stops working along with the old password. */
  @Transactional
  public void changePassword(Long userId, ChangePasswordRequest request) {
    requireBcryptLength(request.newPassword());
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, USER_NOT_FOUND));
    if (loginThrottle.isBlocked(user.getEmail())) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_FAILED_LOGINS);
    }
    if (!passwordMatches(request.currentPassword(), user)) {
      loginThrottle.recordFailure(user.getEmail());
      throw new ApiException(HttpStatus.BAD_REQUEST, WRONG_CURRENT_PASSWORD);
    }
    loginThrottle.reset(user.getEmail());
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    tokenService.revokeAll(userId);
    auditService.record(userId, "PASSWORD_CHANGED", AUDIT_ENTITY, userId);
  }

  /** Sign-up after the email was verified by a code (AccountVerificationService). */
  @Transactional
  public AuthResponse registerVerified(String name, String email, String password) {
    return register(new RegisterRequest(name, email, password));
  }

  /**
   * Sets a new password after the email was verified by a code: every session of the account is
   * signed out and failed sign-in attempts are forgotten.
   */
  @Transactional
  public void resetPassword(String email, String newPassword) {
    requireBcryptLength(newPassword);
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, USER_NOT_FOUND));
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    tokenService.revokeAll(user.getUserId());
    loginThrottle.reset(email);
    auditService.record(user.getUserId(), "PASSWORD_RESET", AUDIT_ENTITY, user.getUserId());
  }

  private static void requireBcryptLength(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
      throw new ApiException(HttpStatus.BAD_REQUEST, PASSWORD_TOO_LONG);
    }
  }

  private User createTraveller(RegisterRequest request, String email) {
    try {
      return userService.createUser(request.name(), email, request.password(), RoleName.TRAVELLER);
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN);
    }
  }

  /** Always runs one bcrypt check so response time does not reveal whether the email exists. */
  private boolean passwordMatches(String rawPassword, User user) {
    boolean hasPassword = user != null && user.getPasswordHash() != null;
    String hash = hasPassword ? user.getPasswordHash() : decoyPasswordHash;
    return passwordEncoder.matches(rawPassword, hash) && hasPassword;
  }
}
