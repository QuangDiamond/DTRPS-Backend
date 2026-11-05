package com.dtrps.modules.auth.service;

/** Checks a Google access token from the web sign-in popup. */
public interface GoogleTokenVerifier {
  /**
   * Returns the account the token belongs to, or throws {@code ApiException} (401) when the token
   * is invalid, expired or was issued to another app.
   */
  GoogleAccount verify(String accessToken);
}
