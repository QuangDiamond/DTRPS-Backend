package com.dtrps.modules.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cổng thanh toán PayOS. Key chỉ nằm ở server ({@code .env.local} hoặc biến môi trường); thiếu key
 * thì các API thanh toán trả 503. {@code webUrl}: địa chỉ web để PayOS đưa đối tác quay lại.
 */
@ConfigurationProperties("app.payos")
public record PayOsProperties(
    String clientId,
    String apiKey,
    String checksumKey,
    String baseUrl,
    String webUrl,
    Duration linkTtl,
    boolean jobsEnabled) {
  public boolean configured() {
    return hasText(clientId) && hasText(apiKey) && hasText(checksumKey);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
