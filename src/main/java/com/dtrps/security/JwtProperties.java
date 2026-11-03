package com.dtrps.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
    @NotBlank @Size(min = 32) String secret,
    @NotBlank String issuer,
    @NotNull Duration accessTtl,
    @NotNull Duration refreshTtl) {}
