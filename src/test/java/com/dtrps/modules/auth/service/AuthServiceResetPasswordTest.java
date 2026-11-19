package com.dtrps.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.auth.dto.ResetPasswordRequest;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import com.dtrps.modules.user.service.UserService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit test AuthService.resetPassword — sheet Auth, UTC020–UTC022 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class AuthServiceResetPasswordTest {
  private static final Long USER_ID = 5L;
  private static final String EMAIL = "a@gmail.com";
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Mock private UserRepository users;
  @Mock private UserService userService;
  @Mock private TokenService tokenService;
  @Mock private LoginThrottle loginThrottle;
  @Mock private AuditService auditService;
  @Mock private PasswordEncoder passwordEncoder;

  private AuthService service;
  private User user;

  @BeforeEach
  void setUp() {
    lenient()
        .when(passwordEncoder.encode(anyString()))
        .thenAnswer(call -> "hash:" + call.getArgument(0));
    service =
        new AuthService(
            users, userService, tokenService, loginThrottle, auditService, passwordEncoder);
    user = new User();
    user.setUserId(USER_ID);
    user.setEmail(EMAIL);
    user.setPasswordHash("old-hash");
    lenient().when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
  }

  /** {@code ascii} ký tự ASCII + {@code vietnamese} ký tự "ễ" (3 byte mỗi ký tự trong UTF-8) */
  private static String password(int ascii, int vietnamese) {
    return "Aa1" + "x".repeat(ascii - 3) + "ễ".repeat(vietnamese);
  }

  @Test
  @DisplayName("UTC020 Valid new password → hash replaced, all sessions revoked, throttle reset")
  void valid_resets() {
    assertThat(VALIDATOR.validate(new ResetPasswordRequest("t", "NewPass@123"))).isEmpty();

    service.resetPassword(EMAIL, "NewPass@123");

    assertThat(user.getPasswordHash()).isEqualTo("hash:NewPass@123");
    verify(tokenService).revokeAll(USER_ID);
    verify(loginThrottle).reset(EMAIL);
    verify(auditService).record(USER_ID, "PASSWORD_RESET", "User", USER_ID);
  }

  @Test
  @DisplayName("UTC021 Password of 73 bytes (passes the 72-character rule) → 400")
  void seventyThreeBytes_badRequest() {
    String tooLong = password(10, 21); // 10 + 63 = 73 byte, 31 ký tự
    assertThat(tooLong.getBytes(StandardCharsets.UTF_8)).hasSize(73);
    assertThat(VALIDATOR.validate(new ResetPasswordRequest("t", tooLong))).isEmpty();

    ApiException e =
        catchThrowableOfType(() -> service.resetPassword(EMAIL, tooLong), ApiException.class);

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).isEqualTo("Mật khẩu quá dài");
    assertThat(user.getPasswordHash()).isEqualTo("old-hash");
    verify(tokenService, never()).revokeAll(anyLong());
  }

  @Test
  @DisplayName("UTC022 Password of exactly 72 bytes → accepted")
  void seventyTwoBytes_accepted() {
    String atLimit = password(9, 21); // 9 + 63 = 72 byte
    assertThat(atLimit.getBytes(StandardCharsets.UTF_8)).hasSize(72);

    service.resetPassword(EMAIL, atLimit);

    assertThat(user.getPasswordHash()).isEqualTo("hash:" + atLimit);
  }

  @Test
  @DisplayName("Email without account → 400")
  void unknownEmail_badRequest() {
    when(users.findByEmail("x@gmail.com")).thenReturn(Optional.empty());

    ApiException e =
        catchThrowableOfType(
            () -> service.resetPassword("x@gmail.com", "NewPass@123"), ApiException.class);

    assertThat(e.getMessage()).isEqualTo("Phiên đăng nhập không hợp lệ");
  }
}
