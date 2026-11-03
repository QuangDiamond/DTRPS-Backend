package com.dtrps.modules.advertisement.entity;

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

@Entity
@Table(
    name = "advertisement_revisions",
    indexes = {
      @Index(name = "idx_ad_revisions_advertisement", columnList = "advertisement_id"),
      @Index(name = "idx_ad_revisions_proposer", columnList = "proposed_by"),
      @Index(name = "idx_ad_revisions_reviewer", columnList = "reviewed_by")
    })
@Check(
    name = "chk_ad_revision_dates",
    constraints = "(((start_at IS NULL) OR (end_at IS NULL) OR (end_at > start_at)))")
@Check(
    name = "chk_ad_revision_status",
    constraints = "status IN ('PENDING', 'APPROVED', 'REJECTED')")
@Getter
@Setter
public class AdvertisementRevision {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "revision_id")
  private Long revisionId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "advertisement_id", nullable = false)
  private Advertisement advertisement;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "proposed_by", nullable = false)
  private User proposedBy;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "image_url", columnDefinition = "text")
  private String imageUrl;

  @Column(name = "target_url", columnDefinition = "text")
  private String targetUrl;

  @Column(name = "start_at")
  private Instant startAt;

  @Column(name = "end_at")
  private Instant endAt;

  @ColumnDefault("'PENDING'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "PENDING";

  @Column(name = "review_note", columnDefinition = "text")
  private String reviewNote;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewed_by")
  private User reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
