package com.dtrps.modules.admin.service;

import com.dtrps.modules.admin.entity.AuditLog;
import com.dtrps.modules.admin.repository.AuditLogRepository;
import com.dtrps.modules.user.repository.UserRepository;
import java.util.Optional;
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
    record(actorId, action, entityName, entityId, null);
  }

  /** {@code description} keeps the reason an admin gave, e.g. why a profile was rejected. */
  public void record(
      Long actorId, String action, String entityName, Long entityId, String description) {
    AuditLog log = new AuditLog();
    log.setUser(userRepository.getReferenceById(actorId));
    log.setAction(action);
    log.setEntityName(entityName);
    log.setEntityId(entityId);
    log.setDescription(description);
    auditLogRepository.save(log);
  }

  /** The reason attached to the latest of the given actions on an entity, if any. */
  public Optional<String> latestDescription(String entityName, Long entityId, String action) {
    return auditLogRepository
        .findFirstByEntityNameAndEntityIdAndActionOrderByLogIdDesc(entityName, entityId, action)
        .map(AuditLog::getDescription);
  }
}
