package com.dtrps.modules.payment.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Đối tác báo đã chuyển tiền nhưng đơn chưa được ghi nhận. Mỗi đơn chỉ có một phiếu đang mở (index
 * {@code uq_payment_issue_open} trong post-schema.sql).
 */
@Entity
@Table(
    name = "payment_issues",
    indexes = {
      @Index(name = "idx_payment_issues_payment", columnList = "payment_id"),
      @Index(name = "idx_payment_issues_status", columnList = "status")
    })
@Check(name = "chk_payment_issue_status", constraints = "status IN ('OPEN', 'RESOLVED', 'REJECTED')")
@Check(name = "chk_payment_issue_amount", constraints = "transferred_amount > 0")
@Getter
@Setter
public class PaymentIssue {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "issue_id")
  private Long issueId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payment_id", nullable = false)
  private Payment payment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reporter_id", nullable = false)
  private User reporter;

  @Column(name = "transferred_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal transferredAmount;

  @Column(name = "transferred_at", nullable = false)
  private Instant transferredAt;

  @Column(name = "bank_reference", nullable = false, length = 100)
  private String bankReference;

  @Column(name = "note", columnDefinition = "text")
  private String note;

  @Column(name = "receipt_url", nullable = false, columnDefinition = "text")
  private String receiptUrl;

  @ColumnDefault("'OPEN'")
  @Column(name = "status", nullable = false, length = 20)
  private String status = "OPEN";

  @Column(name = "resolution_note", columnDefinition = "text")
  private String resolutionNote;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "handled_by")
  private User handledBy;

  @Column(name = "handled_at")
  private Instant handledAt;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
