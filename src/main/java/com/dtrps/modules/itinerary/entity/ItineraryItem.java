package com.dtrps.modules.itinerary.entity;

import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.TransportMode;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "itinerary_items",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_itinerary_day_order",
          columnNames = {"day_id", "item_order"})
    },
    indexes = {
      @Index(name = "idx_itinerary_items_place", columnList = "place_id"),
      @Index(name = "idx_itinerary_items_transport", columnList = "transport_mode_id")
    })
@Check(
    name = "chk_itinerary_item_cost",
    constraints = "(((estimated_cost IS NULL) OR (estimated_cost >= (0)::numeric)))")
@Check(name = "chk_itinerary_item_order", constraints = "((item_order > 0))")
@Check(
    name = "chk_itinerary_item_status",
    constraints = "status IN ('PLANNED', 'COMPLETED', 'SKIPPED', 'CANCELLED')")
@Check(
    name = "chk_itinerary_item_time",
    constraints = "(((start_time IS NULL) OR (end_time IS NULL) OR (end_time > start_time)))")
@Check(
    name = "chk_itinerary_travel_time",
    constraints = "(((travel_time_minutes IS NULL) OR (travel_time_minutes >= 0)))")
@Getter
@Setter
public class ItineraryItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "item_id")
  private Long itemId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "day_id", nullable = false)
  private ItineraryDay day;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "place_id", nullable = false)
  private Place place;

  @Column(name = "item_order", nullable = false)
  private Integer itemOrder;

  @Column(name = "start_time")
  private LocalTime startTime;

  @Column(name = "end_time")
  private LocalTime endTime;

  @Column(name = "estimated_cost", precision = 15, scale = 2)
  private BigDecimal estimatedCost;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transport_mode_id")
  private TransportMode transportMode;

  @Column(name = "travel_time_minutes")
  private Integer travelTimeMinutes;

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @ColumnDefault("'PLANNED'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "PLANNED";

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
