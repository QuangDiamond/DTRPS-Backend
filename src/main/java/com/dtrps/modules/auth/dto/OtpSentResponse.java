package com.dtrps.modules.auth.dto;

/**
 * A code was emailed: it works for {@code expiresInSeconds}; another can be asked for after {@code
 * resendInSeconds}.
 */
public record OtpSentResponse(String email, long expiresInSeconds, long resendInSeconds) {}
