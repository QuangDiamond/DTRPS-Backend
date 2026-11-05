package com.dtrps.modules.auth.dto;

/** The session, and whether this Google sign-in just created the account (first-time setup). */
public record GoogleSignInResponse(AuthResponse session, boolean newAccount) {}
