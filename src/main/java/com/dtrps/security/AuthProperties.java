package com.dtrps.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
    @Min(1) int rateLimitPerMinute,
    @Min(1) int maxFailedLogins,
    @NotNull Duration loginLockDuration) {}
