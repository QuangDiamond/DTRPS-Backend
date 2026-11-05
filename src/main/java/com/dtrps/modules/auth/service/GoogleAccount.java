package com.dtrps.modules.auth.service;

/** What Google says about the signed-in person, after the token was checked. */
public record GoogleAccount(String email, boolean emailVerified, String name, String picture) {}
