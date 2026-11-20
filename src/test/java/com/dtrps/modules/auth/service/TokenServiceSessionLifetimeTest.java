package com.dtrps.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** Admin: phiên tối đa 8 giờ, làm mới không kéo dài · người dùng thường: trượt 30 ngày. */
@ExtendWith(MockitoExtension.class)
class TokenServiceSessionLifetimeTest {
  private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");
  private static final Duration REFRESH_TTL = Duration.ofDays(30);
  private static final Duration ADMIN_MAX = Duration.ofHours(8);

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

  @Test
  @DisplayName("Đăng nhập admin → refresh token hết hạn sau 8 giờ")
  void issue_admin_capsAtAdminMax() {
    tokenService.issue(user("ADMIN"));
    assertThat(savedExpiry()).isEqualTo(NOW.plus(ADMIN_MAX));
  }

  @Test
  @DisplayName("Đăng nhập du khách → refresh token hết hạn sau 30 ngày")
  void issue_traveller_usesRefreshTtl() {
    tokenService.issue(user("TRAVELLER"));
    assertThat(savedExpiry()).isEqualTo(NOW.plus(REFRESH_TTL));
  }

  @Test
  @DisplayName("Admin làm mới token → giữ nguyên mốc hết hạn của lần đăng nhập")
  void rotate_admin_keepsOriginalExpiry() {
    Instant loginEnds = NOW.plus(Duration.ofHours(2));
    stored(user("ADMIN"), loginEnds);
    tokenService.rotate("raw");
    assertThat(savedExpiry()).isEqualTo(loginEnds);
  }

  @Test
  @DisplayName("Biên: token admin cũ (trước khi có giới hạn) còn 30 ngày → cắt về 8 giờ")
  void rotate_adminLegacyToken_isCappedToAdminMax() {
    stored(user("ADMIN"), NOW.plus(Duration.ofDays(29)));
    tokenService.rotate("raw");
    assertThat(savedExpiry()).isEqualTo(NOW.plus(ADMIN_MAX));
  }

  @Test
  @DisplayName("Du khách làm mới token → trượt thêm 30 ngày")
  void rotate_traveller_slides() {
    stored(user("TRAVELLER"), NOW.plus(Duration.ofDays(1)));
    tokenService.rotate("raw");
    assertThat(savedExpiry()).isEqualTo(NOW.plus(REFRESH_TTL));
  }

  @Test
  @DisplayName("Không cấu hình admin-session-max → mặc định 8 giờ")
  void properties_defaultAdminMax() {
    JwtProperties properties = new JwtProperties("s".repeat(32), "dtrps", REFRESH_TTL, REFRESH_TTL);
    assertThat(properties.adminSessionMax()).isEqualTo(Duration.ofHours(8));
  }

  private void stored(User user, Instant expiresAt) {
    RefreshToken token = new RefreshToken();
    token.setUser(user);
    token.setFamilyId(UUID.randomUUID());
    token.setExpiresAt(expiresAt);
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
  }

  private Instant savedExpiry() {
    ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository, atLeastOnce()).save(saved.capture());
    List<RefreshToken> all = saved.getAllValues();
    return all.get(all.size() - 1).getExpiresAt();
  }

  private static User user(String roleName) {
    Role role = new Role();
    role.setRoleName(roleName);
    User user = new User();
    user.setUserId(1L);
    user.setStatus(TokenService.USER_ACTIVE);
    user.getRoles().add(role);
    return user;
  }
}
