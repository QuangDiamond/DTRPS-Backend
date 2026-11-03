package com.dtrps.modules.admin.service;

import com.dtrps.modules.admin.entity.AuditLog;
import com.dtrps.modules.admin.repository.AuditLogRepository;
import com.dtrps.modules.user.repository.UserRepository;
import org.springframework.stereotype.Service;

/** Runs in the caller's transaction, so an action and its audit row commit together. */
@Service
public class AuditService {
  private final AuditLogRepository auditLogRepository;
  private final UserRepository userRepository;

  public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
    this.auditLogRepository = auditLogRepository;
    this.userRepository = userRepository;
  }

  public void record(Long actorId, String action, String entityName, Long entityId) {
    AuditLog log = new AuditLog();
    log.setUser(userRepository.getReferenceById(actorId));
    log.setAction(action);
    log.setEntityName(entityName);
    log.setEntityId(entityId);
    auditLogRepository.save(log);
  }
}
