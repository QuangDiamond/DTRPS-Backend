package com.dtrps.modules.admin.dto;

import java.time.Instant;
import java.util.Map;

/** Status of the external services the system depends on. Never carries a key or secret. */
public final class IntegrationDtos {
  private IntegrationDtos() {}

  /**
   * {@code status}: OK, ERROR, NOT_CONFIGURED, UNCHECKED. {@code checkable}: the page can run a live
   * check. {@code details}: safe facts only (usage, last webhook, host…).
   */
  public record IntegrationStatus(
      String key,
      String name,
      String purpose,
      boolean configured,
      boolean checkable,
      String status,
      String message,
      Long latencyMs,
      Instant checkedAt,
      Map<String, Object> details) {}
}
