package com.dtrps.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.auth.entity.RefreshToken;
import com.dtrps.modules.auth.repository.RefreshTokenRepository;
import com.dtrps.modules.user.entity.Role;
import com.dtrps.modules.user.entity.User;
import com.dtrps.security.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/**
 * Phiên đăng nhập — sheet Auth, UTC029–UTC037 trong Report5.1. Admin: phiên tối đa 8 giờ, làm mới
 * không kéo dài · người dùng thường: trượt 30 ngày mỗi lần làm mới.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceSessionLifetimeTest {
  private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");
  private static final Duration REFRESH_TTL = Duration.ofDays(30);
  private static final Duration ADMIN_MAX = Duration.ofHours(8);
  private static final String INVALID = "Phiên đăng nhập không hợp lệ hoặc đã hết hạn";

  @Mock private JwtEncoder jwtEncoder;
  @Mock private RefreshTokenRepository refreshTokenRepository;

  private TokenService tokenService;

  @BeforeEach
  void setUp() {
    JwtProperties properties =
        new JwtProperties(
            "test-only-secret-test-only-secret-0123456789",
            "dtrps",
            Duration.ofMinutes(15),
            REFRESH_TTL,
            ADMIN_MAX);
    tokenService =
        new TokenService(
            jwtEncoder, refreshTokenRepository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    lenient()
        .when(jwtEncoder.encode(any()))
        .thenReturn(
            Jwt.withTokenValue("jwt")
                .header("alg", "HS256")
                .claim("sub", "1")
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(900))
                .build());
  }

  @Nested
  @DisplayName("issue")
  class Issue {
    @Test
    @DisplayName("UTC029 Admin signs in → refresh token expires after 8 hours")
    void admin_capsAtAdminMax() {
      tokenService.issue(user("ADMIN", TokenService.USER_ACTIVE));

      assertThat(savedExpiry()).isEqualTo(NOW.plus(ADMIN_MAX));
    }

    @Test
    @DisplayName("UTC030 Traveller signs in → refresh token expires after 30 days")
    void traveller_usesRefreshTtl() {
      tokenService.issue(user("TRAVELLER", TokenService.USER_ACTIVE));

      assertThat(savedExpiry()).isEqualTo(NOW.plus(REFRESH_TTL));
    }

    @Test
    @DisplayName("UTC031 admin-session-max not configured → defaults to 8 hours")
    void properties_defaultAdminMax() {
      JwtProperties properties =
          new JwtProperties("s".repeat(32), "dtrps", REFRESH_TTL, REFRESH_TTL);

      assertThat(properties.adminSessionMax()).isEqualTo(Duration.ofHours(8));
    }
  }

  @Nested
  @DisplayName("rotate")
  class Rotate {
    @Test
    @DisplayName("UTC032 Admin refreshes → keeps the expiry of the original sign-in")
    void admin_keepsOriginalExpiry() {
      Instant loginEnds = NOW.plus(Duration.ofHours(2));
      RefreshToken current = stored(user("ADMIN", TokenService.USER_ACTIVE), loginEnds);

      tokenService.rotate("raw");

      assertThat(savedExpiry()).isEqualTo(loginEnds);
      assertThat(current.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("UTC033 Traveller refreshes → session slides another 30 days")
    void traveller_slides() {
      stored(user("TRAVELLER", TokenService.USER_ACTIVE), NOW.plus(Duration.ofDays(1)));

      tokenService.rotate("raw");

      assertThat(savedExpiry()).isEqualTo(NOW.plus(REFRESH_TTL));
    }

    @Test
    @DisplayName("UTC034 Old admin token with 29 days left (before the cap) → cut to 8 hours")
    void adminLegacyToken_isCapped() {
      stored(user("ADMIN", TokenService.USER_ACTIVE), NOW.plus(Duration.ofDays(29)));

      tokenService.rotate("raw");

      assertThat(savedExpiry()).isEqualTo(NOW.plus(ADMIN_MAX));
    }

    @Test
    @DisplayName("UTC035 Replaying an already used token → 401, whole session revoked")
    void replayedToken_revokesFamily() {
      RefreshToken used = stored(user("TRAVELLER", TokenService.USER_ACTIVE), NOW.plusSeconds(60));
      used.setRevokedAt(NOW.minusSeconds(5));

      ApiException e = catchThrowableOfType(() -> tokenService.rotate("raw"), ApiException.class);

      assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(e.getMessage()).isEqualTo(INVALID);
      verify(refreshTokenRepository).revokeFamily(used.getFamilyId(), NOW);
      verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTC036 Admin session reached its 8-hour end (expiresAt = now) → 401")
    void adminSessionEnded_unauthorized() {
      stored(user("ADMIN", TokenService.USER_ACTIVE), NOW);

      ApiException e = catchThrowableOfType(() -> tokenService.rotate("raw"), ApiException.class);

      assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(e.getMessage()).isEqualTo(INVALID);
      verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTC037 Account locked while signed in → 403, session revoked")
    void lockedAccount_forbidden() {
      RefreshToken current = stored(user("ADMIN", "SUSPENDED"), NOW.plus(Duration.ofHours(3)));

      ApiException e = catchThrowableOfType(() -> tokenService.rotate("raw"), ApiException.class);

      assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(e.getMessage()).isEqualTo("Tài khoản đã bị khóa");
      verify(refreshTokenRepository).revokeFamily(current.getFamilyId(), NOW);
    }
  }

  private RefreshToken stored(User user, Instant expiresAt) {
    RefreshToken token = new RefreshToken();
    token.setUser(user);
    token.setFamilyId(UUID.randomUUID());
    token.setExpiresAt(expiresAt);
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    return token;
  }

  private Instant savedExpiry() {
    ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository, atLeastOnce()).save(saved.capture());
    List<RefreshToken> all = saved.getAllValues();
    return all.get(all.size() - 1).getExpiresAt();
  }

  private static User user(String roleName, String status) {
    Role role = new Role();
    role.setRoleName(roleName);
    User user = new User();
    user.setUserId(1L);
    user.setStatus(status);
    user.getRoles().add(role);
    return user;
  }
}
