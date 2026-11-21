package com.dtrps.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.auth.config.GoogleProperties;
import com.dtrps.modules.auth.dto.GoogleSignInResponse;
import com.dtrps.modules.auth.dto.TokenPair;
import com.dtrps.modules.user.entity.Role;
import com.dtrps.modules.user.entity.RoleName;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import com.dtrps.modules.user.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

/** Unit test GoogleSignInService — sheet Auth, UTC023–UTC028 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class GoogleSignInServiceTest {
  private static final String TOKEN = "google-access-token";
  private static final String EMAIL = "a@gmail.com";
  private static final String PICTURE = "https://lh3.googleusercontent.com/a.jpg";

  @Mock private GoogleTokenVerifier verifier;
  @Mock private UserRepository users;
  @Mock private UserService userService;
  @Mock private TokenService tokenService;
  @Mock private AuditService auditService;

  private GoogleSignInService service;

  @BeforeEach
  void setUp() {
    service = signInWith("client-id.apps.googleusercontent.com");
    lenient()
        .when(tokenService.issue(any(User.class)))
        .thenReturn(new TokenPair("access", "refresh", 900));
  }

  private GoogleSignInService signInWith(String clientId) {
    return new GoogleSignInService(
        new GoogleProperties(clientId), verifier, users, userService, tokenService, auditService);
  }

  private void googleSays(boolean verified) {
    when(verifier.verify(TOKEN))
        .thenReturn(new GoogleAccount("A@Gmail.com", verified, "Nguyễn Văn A", PICTURE));
  }

  private static User user(long id, String status, String role) {
    User user = new User();
    user.setUserId(id);
    user.setEmail(EMAIL);
    user.setStatus(status);
    Role granted = new Role();
    granted.setRoleName(role);
    user.getRoles().add(granted);
    return user;
  }

  private ApiException error() {
    return catchThrowableOfType(() -> service.signIn(TOKEN), ApiException.class);
  }

  @Test
  @DisplayName("UTC023 New Google email → TRAVELLER created with Google name and avatar")
  void newEmail_createsTraveller() {
    googleSays(true);
    User created = user(20, "ACTIVE", "TRAVELLER");
    when(userService.createUser("Nguyễn Văn A", EMAIL, null, RoleName.TRAVELLER))
        .thenReturn(created);

    GoogleSignInResponse response = service.signIn(TOKEN);

    assertThat(response.newAccount()).isTrue();
    assertThat(response.session().accessToken()).isEqualTo("access");
    assertThat(created.getAvatarUrl()).isEqualTo(PICTURE);
    verify(auditService).record(20L, "REGISTER_GOOGLE", "User", 20L);
  }

  @Test
  @DisplayName("UTC024 Email of an existing traveller → signed in, no new account")
  void existingTraveller_signsIn() {
    googleSays(true);
    User existing = user(10, "ACTIVE", "TRAVELLER");
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

    GoogleSignInResponse response = service.signIn(TOKEN);

    assertThat(response.newAccount()).isFalse();
    assertThat(response.session().user().id()).isEqualTo(10L);
    verify(userService, never()).createUser(anyString(), anyString(), any(), any());
    verify(auditService).record(10L, "LOGIN_GOOGLE", "User", 10L);
  }

  @Test
  @DisplayName("UTC025 Google email not verified → 401")
  void unverifiedEmail_unauthorized() {
    googleSays(false);

    ApiException e = error();

    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(e.getMessage()).isEqualTo("Email Google này chưa được xác minh");
    verifyNoInteractions(users, userService, tokenService);
  }

  @Test
  @DisplayName("UTC026 Email of an ADMIN account → 403")
  void admin_forbidden() {
    googleSays(true);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(1, "ACTIVE", "ADMIN")));

    ApiException e = error();

    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(e.getMessage()).isEqualTo("Tài khoản quản trị chỉ đăng nhập bằng email và mật khẩu");
    verify(tokenService, never()).issue(any());
  }

  @Test
  @DisplayName("UTC027 Locked account → 403")
  void locked_forbidden() {
    googleSays(true);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(10, "SUSPENDED", "TRAVELLER")));

    ApiException e = error();

    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(e.getMessage()).isEqualTo("Tài khoản đã bị khóa");
  }

  @Test
  @DisplayName("UTC028 Google client id not configured → 503, Google never called")
  void notConfigured_unavailable() {
    service = signInWith(" ");

    ApiException e = error();

    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(e.getMessage()).isEqualTo("Đăng nhập Google chưa được cấu hình");
    verifyNoInteractions(verifier);
  }

  @Test
  @DisplayName("Two clicks at the same time create the same email → 409, click again")
  void concurrentCreate_conflict() {
    googleSays(true);
    when(userService.createUser(anyString(), eq(EMAIL), any(), eq(RoleName.TRAVELLER)))
        .thenThrow(new DataIntegrityViolationException("uq_users_email"));

    ApiException e = error();

    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(e.getMessage()).isEqualTo("Vui lòng bấm Tiếp tục với Google thêm lần nữa");
  }

  @Test
  @DisplayName("UTC046 Email of a BUSINESS_OWNER account → 403 wrong portal")
  void partner_forbidden() {
    googleSays(true);
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(12, "ACTIVE", "BUSINESS_OWNER")));

    ApiException e = error();

    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(e.getMessage())
        .isEqualTo("Đây là tài khoản Đối tác. Vui lòng đăng nhập tại cổng Đối tác.");
    verify(tokenService, never()).issue(any());
  }
}
