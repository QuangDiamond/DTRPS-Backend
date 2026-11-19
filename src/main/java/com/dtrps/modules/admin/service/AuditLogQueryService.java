package com.dtrps.modules.admin.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.dto.AuditDtos.Actor;
import com.dtrps.modules.admin.dto.AuditDtos.AuditFilters;
import com.dtrps.modules.admin.dto.AuditDtos.AuditLogItem;
import com.dtrps.modules.admin.entity.AuditLog;
import com.dtrps.modules.admin.repository.AuditLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the admin activity log: who did what, to which record, and why. */
@Service
public class AuditLogQueryService {
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
  /** Một lần lọc tối đa 1 năm, tránh quét cả bảng */
  private static final int MAX_RANGE_DAYS = 366;

  private final AuditLogRepository logs;

  public AuditLogQueryService(AuditLogRepository logs) {
    this.logs = logs;
  }

  @Transactional(readOnly = true)
  public PageResponse<AuditLogItem> search(
      String keyword,
      String action,
      Long actorId,
      LocalDate from,
      LocalDate to,
      Pageable pageable) {
    if (from != null && to != null) {
      if (to.isBefore(from)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
      }
      if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ lọc tối đa 1 năm mỗi lần");
      }
    }
    Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) -> {
                var user = root.join("user", jakarta.persistence.criteria.JoinType.LEFT);
                return cb.or(
                    cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like),
                    cb.like(cb.lower(root.get("action")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("entityName"), "")), like),
                    cb.like(cb.lower(cb.coalesce(user.get("fullName"), "")), like),
                    cb.like(cb.lower(cb.coalesce(user.get("email"), "")), like));
              });
    }
    if (action != null && !action.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
    }
    if (actorId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("user").get("userId"), actorId));
    }
    if (from != null) {
      Instant start = from.atStartOfDay(VIETNAM).toInstant();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
    }
    if (to != null) {
      Instant end = to.plusDays(1).atStartOfDay(VIETNAM).toInstant();
      spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
    }
    return PageResponse.from(logs.findAll(spec, pageable).map(AuditLogQueryService::toItem));
  }

  @Transactional(readOnly = true)
  public AuditFilters filters() {
    return new AuditFilters(
        logs.findDistinctActions(),
        logs.findActors().stream()
            .map(row -> new Actor((Long) row[0], (String) row[1], (String) row[2]))
            .toList());
  }

  private static AuditLogItem toItem(AuditLog log) {
    var user = log.getUser();
    return new AuditLogItem(
        log.getLogId(),
        log.getAction(),
        log.getEntityName(),
        log.getEntityId(),
        log.getDescription(),
        user == null ? null : user.getUserId(),
        user == null ? null : user.getFullName(),
        user == null ? null : user.getEmail(),
        log.getCreatedAt());
  }
}
