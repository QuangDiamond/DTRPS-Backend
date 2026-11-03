package com.dtrps.modules.auth.dto;

public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}
