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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "itineraries",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_itinerary_plan_version",
          columnNames = {"plan_id", "version_number"})
    })
@Check(
    name = "chk_itinerary_generation_source",
    constraints = "generation_source IN ('USER', 'SYSTEM', 'AI')")
@Check(name = "chk_itinerary_status", constraints = "status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')")
@Check(name = "chk_itinerary_version", constraints = "((version_number > 0))")
@Getter
@Setter
public class Itinerary {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "itinerary_id")
  private Long itineraryId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private TravelPlan plan;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @ColumnDefault("1")
  @Column(name = "version_number", nullable = false)
  private Integer versionNumber = 1;

  @ColumnDefault("'SYSTEM'")
  @Column(name = "generation_source", nullable = false, length = 30)
  private String generationSource = "SYSTEM";

  @ColumnDefault("'DRAFT'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "DRAFT";

  @ColumnDefault("'VND'")
  @Column(name = "currency", nullable = false, length = 3)
  private String currency = "VND";

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
