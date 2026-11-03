package com.dtrps.modules.auth.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.auth.dto.RotatedSession;
import com.dtrps.modules.auth.dto.TokenPair;
import com.dtrps.modules.auth.entity.RefreshToken;
import com.dtrps.modules.auth.repository.RefreshTokenRepository;
import com.dtrps.modules.user.entity.Role;
import com.dtrps.modules.user.entity.User;
import com.dtrps.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Callers own the transaction: {@link AuthService} wraps every method used here. */
@Service
public class TokenService {
  public static final String ROLES_CLAIM = "roles";
  public static final String USER_ACTIVE = "ACTIVE";

  private static final int REFRESH_TOKEN_BYTES = 32;
  private static final String INVALID_REFRESH_TOKEN =
      "Phiên đăng nhập không hợp lệ hoặc đã hết hạn";
  private static final String ACCOUNT_LOCKED = "Tài khoản đã bị khóa";

  private final SecureRandom secureRandom = new SecureRandom();
  private final JwtEncoder jwtEncoder;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;
  private final Clock clock;

  public TokenService(
      JwtEncoder jwtEncoder,
      RefreshTokenRepository refreshTokenRepository,
      JwtProperties jwtProperties,
      Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtProperties = jwtProperties;
    this.clock = clock;
  }

  public TokenPair issue(User user) {
    refreshTokenRepository.deleteExpiredForUser(user.getUserId(), clock.instant());
    return createPair(user, UUID.randomUUID());
  }

  /** Replaces a refresh token. Replaying a used token ends the whole login session. */
  public RotatedSession rotate(String rawToken) {
    Instant now = clock.instant();
    RefreshToken current =
        refreshTokenRepository
            .findByTokenHash(hash(rawToken))
            .orElseThrow(TokenService::invalidRefreshToken);
    if (current.getRevokedAt() != null) {
      refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
      throw invalidRefreshToken();
    }
    if (!current.getExpiresAt().isAfter(now)) {
      throw invalidRefreshToken();
    }
    User user = current.getUser();
    if (!USER_ACTIVE.equals(user.getStatus())) {
      refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
      throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_LOCKED);
    }
    current.setRevokedAt(now);
    return new RotatedSession(user, createPair(user, current.getFamilyId()));
  }

  public void revoke(String rawToken) {
    refreshTokenRepository
        .findByTokenHash(hash(rawToken))
        .ifPresent(
            token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), clock.instant()));
  }

  public void revokeAll(Long userId) {
    refreshTokenRepository.revokeAllForUser(userId, clock.instant());
  }

  private TokenPair createPair(User user, UUID familyId) {
    Instant now = clock.instant();
    String rawRefreshToken = newRefreshToken();
    RefreshToken entity = new RefreshToken();
    entity.setUser(user);
    entity.setFamilyId(familyId);
    entity.setTokenHash(hash(rawRefreshToken));
    entity.setExpiresAt(now.plus(jwtProperties.refreshTtl()));
    refreshTokenRepository.save(entity);
    return new TokenPair(
        createAccessToken(user, now), rawRefreshToken, jwtProperties.accessTtl().toSeconds());
  }

  private String createAccessToken(User user, Instant now) {
    List<String> roles = user.getRoles().stream().map(Role::getRoleName).sorted().toList();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .id(UUID.randomUUID().toString())
            .issuer(jwtProperties.issuer())
            .subject(String.valueOf(user.getUserId()))
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.accessTtl()))
            .claim(ROLES_CLAIM, roles)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private String newRefreshToken() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String rawToken) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static ApiException invalidRefreshToken() {
    return new ApiException(HttpStatus.UNAUTHORIZED, INVALID_REFRESH_TOKEN);
  }
}
