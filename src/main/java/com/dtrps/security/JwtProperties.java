package com.dtrps.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * {@code refreshTtl}: sliding lifetime of a normal login (renewed on every refresh). {@code
 * adminSessionMax}: hard cap for accounts with the ADMIN role — refreshing never extends it, so an
 * admin signs in again after this long however active they are.
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
    @NotBlank @Size(min = 32) String secret,
    @NotBlank String issuer,
    @NotNull Duration accessTtl,
    @NotNull Duration refreshTtl,
    Duration adminSessionMax) {

  public static final Duration DEFAULT_ADMIN_SESSION_MAX = Duration.ofHours(8);

  @ConstructorBinding
  public JwtProperties {
    if (adminSessionMax == null) adminSessionMax = DEFAULT_ADMIN_SESSION_MAX;
  }

  public JwtProperties(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {
    this(secret, issuer, accessTtl, refreshTtl, null);
  }
}
