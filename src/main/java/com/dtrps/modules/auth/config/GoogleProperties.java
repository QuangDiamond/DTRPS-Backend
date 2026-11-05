package com.dtrps.modules.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google sign-in. {@code clientId} is the web OAuth Client ID (public); a Google access token is
 * only accepted when it was issued to this client. Blank: Google sign-in is turned off.
 */
@ConfigurationProperties("app.google")
public record GoogleProperties(String clientId) {
  public boolean configured() {
    return clientId != null && !clientId.isBlank();
  }
}
