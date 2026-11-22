package com.dtrps.modules.auth.service;

/** Checks a Google token from the web sign-in popup or the mobile Google Sign-In SDK. */
public interface GoogleTokenVerifier {
  /**
   * Returns the account the access token belongs to, or throws {@code ApiException} (401) when the
   * token is invalid, expired or was issued to another app.
   */
  GoogleAccount verify(String accessToken);

  /**
   * Same as {@link #verify} for an OpenID ID token (mobile app). The token must be issued for the
   * web Client ID, which the Android Google Sign-In SDK requests via {@code webClientId}.
   */
  GoogleAccount verifyIdToken(String idToken);
}
