package com.dtrps.modules.admin.repository;

import com.dtrps.modules.admin.entity.AuditLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
  Optional<AuditLog> findFirstByEntityNameAndEntityIdAndActionOrderByLogIdDesc(
      String entityName, Long entityId, String action);
}
