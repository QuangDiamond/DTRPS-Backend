package com.dtrps.modules.poi.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Một người dùng báo cáo một đánh giá địa điểm của người khác (mỗi người một lần cho mỗi đánh giá).
 * Admin xử lý: ẩn đánh giá ({@code RESOLVED}) hoặc bỏ qua vì không vi phạm ({@code DISMISSED}).
 */
@Entity
@Table(
    name = "review_reports",
    indexes = {
      @Index(name = "idx_review_reports_review", columnList = "review_id"),
      @Index(name = "idx_review_reports_status", columnList = "status")
    },
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_review_report_reporter",
            columnNames = {"review_id", "reporter_id"}))
@Check(
    name = "chk_review_report_reason",
    constraints =
        "reason IN ('SPAM', 'OFFENSIVE', 'INAPPROPRIATE', 'MISLEADING', 'FAKE', 'PRIVACY', 'OTHER')")
@Check(
    name = "chk_review_report_status",
    constraints = "status IN ('PENDING', 'RESOLVED', 'DISMISSED')")
@Getter
@Setter
public class PlaceReviewReport {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "report_id")
  private Long reportId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "review_id", nullable = false)
  private PlaceReview review;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reporter_id", nullable = false)
  private User reporter;

  @Column(name = "reason", nullable = false, length = 30)
  private String reason;

  @Column(name = "note", columnDefinition = "text")
  private String note;

  @ColumnDefault("'PENDING'")
  @Column(name = "status", nullable = false, length = 20)
  private String status = "PENDING";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "handled_by")
  private User handledBy;

  @Column(name = "handled_at")
  private Instant handledAt;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
