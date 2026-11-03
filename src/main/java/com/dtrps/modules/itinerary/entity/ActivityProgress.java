package com.dtrps.modules.itinerary.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "activity_progress",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_activity_progress_item",
          columnNames = {"progress_id", "item_id"})
    },
    indexes = {@Index(name = "idx_activity_progress_item", columnList = "item_id")})
@Check(
    name = "chk_activity_progress_status",
    constraints = "status IN ('PENDING', 'COMPLETED', 'SKIPPED')")
@Getter
@Setter
public class ActivityProgress {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "activity_progress_id")
  private Long activityProgressId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "progress_id", nullable = false)
  private TripProgress progress;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "item_id", nullable = false)
  private ItineraryItem item;

  @ColumnDefault("'PENDING'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "PENDING";

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
