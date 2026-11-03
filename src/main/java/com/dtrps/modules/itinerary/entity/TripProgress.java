package com.dtrps.modules.itinerary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "trip_progress")
@Check(
    name = "chk_trip_progress_status",
    constraints = "status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')")
@Getter
@Setter
public class TripProgress {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "progress_id")
  private Long progressId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "itinerary_id", nullable = false, unique = true)
  private Itinerary itinerary;

  @ColumnDefault("'NOT_STARTED'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "NOT_STARTED";

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
