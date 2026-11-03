package com.dtrps.modules.admin.repository;

import com.dtrps.modules.admin.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
