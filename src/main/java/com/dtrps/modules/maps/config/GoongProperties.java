package com.dtrps.modules.maps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Goong REST services (address search, geocoding, directions). {@code apiKey} is the "API Key" from
 * account.goong.io and stays on the server; the web only holds the public Maptiles key. Blank
 * disables the map endpoints (they answer 503).
 */
@ConfigurationProperties("app.goong")
public record GoongProperties(String apiKey, String baseUrl) {
  public boolean configured() {
    return apiKey != null && !apiKey.isBlank();
  }
}
