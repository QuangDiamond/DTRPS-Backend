package com.dtrps.modules.discovery.entity;

import com.dtrps.modules.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "search_logs",
    indexes = {@Index(name = "idx_search_logs_user", columnList = "user_id")})
@Check(
    name = "chk_search_processing_time",
    constraints = "(((processing_time_ms IS NULL) OR (processing_time_ms >= 0)))")
@Check(
    name = "chk_search_result_count",
    constraints = "(((result_count IS NULL) OR (result_count >= 0)))")
@Getter
@Setter
public class SearchLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "search_id")
  private Long searchId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "search_query", nullable = false, columnDefinition = "text")
  private String searchQuery;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "filters", columnDefinition = "jsonb")
  private String filters;

  @Column(name = "result_count")
  private Integer resultCount;

  @Column(name = "processing_time_ms")
  private Integer processingTimeMs;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
