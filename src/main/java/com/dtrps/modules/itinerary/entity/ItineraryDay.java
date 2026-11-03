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

@Entity
@Table(
    name = "itinerary_days",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_itinerary_day_number",
          columnNames = {"itinerary_id", "day_number"})
    })
@Check(name = "chk_itinerary_day_number", constraints = "((day_number > 0))")
@Getter
@Setter
public class ItineraryDay {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "day_id")
  private Long dayId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "itinerary_id", nullable = false)
  private Itinerary itinerary;

  @Column(name = "day_number", nullable = false)
  private Integer dayNumber;

  @Column(name = "title", length = 255)
  private String title;

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
