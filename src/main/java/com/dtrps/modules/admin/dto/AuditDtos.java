package com.dtrps.modules.admin.dto;

import java.time.Instant;
import java.util.List;

/** Admin activity log (audit_logs). */
public final class AuditDtos {
  private AuditDtos() {}

  public record AuditLogItem(
      Long id,
      String action,
      String entityName,
      Long entityId,
      String description,
      Long actorId,
      String actorName,
      String actorEmail,
      Instant createdAt) {}

  public record Actor(Long id, String name, String email) {}

  public record AuditFilters(List<String> actions, List<Actor> actors) {}
}
