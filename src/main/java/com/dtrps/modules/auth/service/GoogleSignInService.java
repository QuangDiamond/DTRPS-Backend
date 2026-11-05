package com.dtrps.modules.auth.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.auth.config.GoogleProperties;
import com.dtrps.modules.auth.dto.AuthResponse;
import com.dtrps.modules.auth.dto.GoogleSignInResponse;
import com.dtrps.modules.user.entity.RoleName;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import com.dtrps.modules.user.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Continue with Google" for travellers and partners. A Google email that already has an account
 * signs in to it (Google has verified the address); a new one gets a traveller account without a
 * password (it can set one later with "Forgot password"). Admin accounts never sign in this way.
 */
@Service
public class GoogleSignInService {
  private static final String AUDIT_ENTITY = "User";
  static final String NOT_CONFIGURED = "Đăng nhập Google chưa được cấu hình";
  static final String EMAIL_NOT_VERIFIED = "Email Google này chưa được xác minh";
  static final String ADMIN_REFUSED = "Tài khoản quản trị chỉ đăng nhập bằng email và mật khẩu";
  static final String ACCOUNT_LOCKED = "Tài khoản đã bị khóa";
  static final String RETRY = "Vui lòng bấm Tiếp tục với Google thêm lần nữa";

  private final GoogleProperties properties;
  private final GoogleTokenVerifier verifier;
  private final UserRepository userRepository;
  private final UserService userService;
  private final TokenService tokenService;
  private final AuditService auditService;

  public GoogleSignInService(
      GoogleProperties properties,
      GoogleTokenVerifier verifier,
      UserRepository userRepository,
      UserService userService,
      TokenService tokenService,
      AuditService auditService) {
    this.properties = properties;
    this.verifier = verifier;
    this.userRepository = userRepository;
    this.userService = userService;
    this.tokenService = tokenService;
    this.auditService = auditService;
  }

  @Transactional
  public GoogleSignInResponse signIn(String accessToken) {
    if (!properties.configured()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, NOT_CONFIGURED);
    }
    GoogleAccount account = verifier.verify(accessToken);
    if (!account.emailVerified()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, EMAIL_NOT_VERIFIED);
    }
    String email = UserService.normalizeEmail(account.email());
    User existing = userRepository.findByEmail(email).orElse(null);
    if (existing != null) {
      if (isAdmin(existing)) {
        throw new ApiException(HttpStatus.FORBIDDEN, ADMIN_REFUSED);
      }
      if (!TokenService.USER_ACTIVE.equals(existing.getStatus())) {
        throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_LOCKED);
      }
      if (existing.getAvatarUrl() == null && account.picture() != null) {
        existing.setAvatarUrl(account.picture());
      }
      auditService.record(existing.getUserId(), "LOGIN_GOOGLE", AUDIT_ENTITY, existing.getUserId());
      return new GoogleSignInResponse(
          AuthResponse.of(existing, tokenService.issue(existing)), false);
    }

    User created;
    try {
      created = userService.createUser(nameOf(account, email), email, null, RoleName.TRAVELLER);
    } catch (DataIntegrityViolationException e) {
      // Hai lần bấm cùng lúc tạo trùng email: lần bấm lại sẽ đăng nhập vào tài khoản vừa tạo
      throw new ApiException(HttpStatus.CONFLICT, RETRY);
    }
    if (account.picture() != null) {
      created.setAvatarUrl(account.picture());
    }
    auditService.record(created.getUserId(), "REGISTER_GOOGLE", AUDIT_ENTITY, created.getUserId());
    return new GoogleSignInResponse(AuthResponse.of(created, tokenService.issue(created)), true);
  }

  private static boolean isAdmin(User user) {
    return user.getRoles().stream()
        .anyMatch(role -> RoleName.ADMIN.name().equals(role.getRoleName()));
  }

  /** Google name, or the part of the email before "@" when Google gives none. */
  private static String nameOf(GoogleAccount account, String email) {
    String name = account.name() == null ? "" : account.name().trim();
    if (name.length() < 2) {
      name = email.substring(0, email.indexOf('@'));
    }
    return name.length() > 150 ? name.substring(0, 150) : name;
  }
}
