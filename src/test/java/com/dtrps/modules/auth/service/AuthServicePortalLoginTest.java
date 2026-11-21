package com.dtrps.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.auth.dto.AuthResponse;
import com.dtrps.modules.auth.dto.LoginRequest;
import com.dtrps.modules.auth.dto.TokenPair;
import com.dtrps.modules.user.entity.Role;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import com.dtrps.modules.user.service.UserService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit test AuthService.login (kiểm tra cổng đăng nhập) — sheet Auth, UTC040–UTC045 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class AuthServicePortalLoginTest {
  private static final String EMAIL = "user@gmail.com";
  private static final String PASSWORD = "Password@123";
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Mock private UserRepository users;
  @Mock private UserService userService;
  @Mock private TokenService tokenService;
  @Mock private LoginThrottle loginThrottle;
  @Mock private AuditService auditService;
  @Mock private PasswordEncoder passwordEncoder;

  private AuthService service;

  @BeforeEach
  void setUp() {
    service =
        new AuthService(
            users, userService, tokenService, loginThrottle, auditService, passwordEncoder);
    lenient().when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);
    lenient()
        .when(tokenService.issue(any(User.class)))
        .thenReturn(new TokenPair("access", "refresh", 900));
  }

  private static User user(long id, String roleName) {
    User user = new User();
    user.setUserId(id);
    user.setEmail(EMAIL);
    user.setFullName("Nam");
    user.setPasswordHash("hash");
    user.setStatus("ACTIVE");
    Role role = new Role();
    role.setRoleName(roleName);
    user.getRoles().add(role);
    return user;
  }

  private static Set<String> messages(Object request) {
    return VALIDATOR.validate(request).stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  @Test
  @DisplayName("UTC040 BUSINESS_OWNER signs in with portal=PARTNER → tokens returned")
  void partnerAtPartnerPortal_ok() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(10, "BUSINESS_OWNER")));

    AuthResponse res = service.login(new LoginRequest(EMAIL, PASSWORD, "PARTNER"));

    assertThat(res.accessToken()).isEqualTo("access");
  }

  @Test
  @DisplayName("UTC041 TRAVELLER signs in with portal=TRAVELLER → tokens returned")
  void travellerAtTravellerPortal_ok() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(11, "TRAVELLER")));

    AuthResponse res = service.login(new LoginRequest(EMAIL, PASSWORD, "TRAVELLER"));

    assertThat(res.accessToken()).isEqualTo("access");
  }

  @Test
  @DisplayName("UTC042 BUSINESS_OWNER signs in with portal=TRAVELLER → 403 wrong portal")
  void partnerAtTravellerPortal_forbidden() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(10, "BUSINESS_OWNER")));

    ApiException e =
        catchThrowableOfType(
            () -> service.login(new LoginRequest(EMAIL, PASSWORD, "TRAVELLER")),
            ApiException.class);

    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(e.getMessage())
        .isEqualTo("Đây là tài khoản Đối tác. Vui lòng đăng nhập tại cổng Đối tác.");
    verify(tokenService, never()).issue(any());
  }

  @Test
  @DisplayName("UTC043 TRAVELLER signs in with portal=PARTNER → 403 wrong portal")
  void travellerAtPartnerPortal_forbidden() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(11, "TRAVELLER")));

    ApiException e =
        catchThrowableOfType(
            () -> service.login(new LoginRequest(EMAIL, PASSWORD, "PARTNER")), ApiException.class);

    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(e.getMessage())
        .isEqualTo("Đây là tài khoản Du khách. Vui lòng đăng nhập tại cổng Du khách.");
    verify(tokenService, never()).issue(any());
  }

  @Test
  @DisplayName("UTC044 portal=null (admin / legacy callers) → skips portal check, tokens returned")
  void nullPortal_skipsCheck() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(1, "ADMIN")));

    AuthResponse res = service.login(new LoginRequest(EMAIL, PASSWORD));

    assertThat(res.accessToken()).isEqualTo("access");
  }

  @Test
  @DisplayName("UTC045 portal=\"ADMIN\" → 400 (validation)")
  void invalidPortal_invalid() {
    assertThat(messages(new LoginRequest(EMAIL, PASSWORD, "ADMIN")))
        .containsExactly("portal phải là TRAVELLER hoặc PARTNER");
  }
}
