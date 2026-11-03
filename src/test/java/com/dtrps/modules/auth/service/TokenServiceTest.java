package com.dtrps.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.auth.entity.RefreshToken;
import com.dtrps.modules.auth.repository.RefreshTokenRepository;
import com.dtrps.modules.user.entity.User;
import com.dtrps.security.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

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
            Duration.ofDays(30));
    tokenService =
        new TokenService(
            jwtEncoder, refreshTokenRepository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("rotate rejects an expired token without revoking its family")
  void rotate_expiredToken_isRejected() {
    RefreshToken token = storedToken(NOW.minusSeconds(1));
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> tokenService.rotate("raw"))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    verify(refreshTokenRepository, never()).revokeFamily(any(), any());
  }

  @Test
  @DisplayName("rotate treats a token expiring exactly now as expired")
  void rotate_tokenExpiringNow_isRejected() {
    RefreshToken token = storedToken(NOW);
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> tokenService.rotate("raw")).isInstanceOf(ApiException.class);
  }

  @Test
  @DisplayName("rotate revokes the family when a revoked token is replayed")
  void rotate_revokedToken_revokesFamily() {
    RefreshToken token = storedToken(NOW.plusSeconds(60));
    token.setRevokedAt(NOW.minusSeconds(5));
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> tokenService.rotate("raw")).isInstanceOf(ApiException.class);

    verify(refreshTokenRepository).revokeFamily(eq(token.getFamilyId()), eq(NOW));
  }

  @Test
  @DisplayName("revoke ends the family of a known token")
  void revoke_knownToken_revokesFamily() {
    RefreshToken token = storedToken(NOW.plusSeconds(60));
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

    tokenService.revoke("raw");

    verify(refreshTokenRepository).revokeFamily(token.getFamilyId(), NOW);
  }

  @Test
  @DisplayName("revoke ignores an unknown token")
  void revoke_unknownToken_doesNothing() {
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

    tokenService.revoke("raw");

    verify(refreshTokenRepository, never()).revokeFamily(any(), any());
  }

  private static RefreshToken storedToken(Instant expiresAt) {
    User user = new User();
    user.setUserId(1L);
    RefreshToken token = new RefreshToken();
    token.setUser(user);
    token.setFamilyId(UUID.randomUUID());
    token.setExpiresAt(expiresAt);
    return token;
  }
}
