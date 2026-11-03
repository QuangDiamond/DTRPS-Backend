package com.dtrps.modules.itinerary.entity;

import com.dtrps.modules.poi.entity.Place;
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

@Entity
@Table(
    name = "plan_places",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_plan_place",
          columnNames = {"plan_id", "place_id"})
    },
    indexes = {@Index(name = "idx_plan_places_place", columnList = "place_id")})
@Check(name = "chk_plan_place_source", constraints = "source IN ('USER', 'SYSTEM', 'AI')")
@Getter
@Setter
public class PlanPlace {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "plan_place_id")
  private Long planPlaceId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private TravelPlan plan;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "place_id", nullable = false)
  private Place place;

  @ColumnDefault("'USER'")
  @Column(name = "source", nullable = false, length = 30)
  private String source = "USER";

  @ColumnDefault("0")
  @Column(name = "priority", nullable = false)
  private Integer priority = 0;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
