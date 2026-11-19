package com.dtrps.modules.admin.repository;

import com.dtrps.modules.admin.entity.AuditLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository
    extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
  Optional<AuditLog> findFirstByEntityNameAndEntityIdAndActionOrderByLogIdDesc(
      String entityName, Long entityId, String action);

  @Override
  @EntityGraph(attributePaths = "user")
  Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);

  /** Actions that appear in the log, for the filter dropdown */
  @Query("select distinct a.action from AuditLog a order by a.action")
  List<String> findDistinctActions();

  /** Users who have log rows → [userId, fullName, email], for the filter dropdown */
  @Query(
      "select distinct u.userId, u.fullName, u.email from AuditLog a join a.user u"
          + " order by u.fullName")
  List<Object[]> findActors();
}
