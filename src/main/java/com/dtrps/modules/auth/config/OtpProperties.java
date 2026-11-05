package com.dtrps.modules.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Email verification codes. {@code ttl}: how long a code works · {@code resendAfter}: wait before
 * asking for another · {@code maxAttempts}: wrong tries before a new code is needed · {@code
 * ticketTtl}: time to finish sign-up / reset after the code was accepted.
 */
@ConfigurationProperties("app.otp")
public record OtpProperties(
    Duration ttl, Duration resendAfter, int maxAttempts, Duration ticketTtl, String fromName) {}
