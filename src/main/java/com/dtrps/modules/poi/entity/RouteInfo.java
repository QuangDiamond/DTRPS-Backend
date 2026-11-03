package com.dtrps.modules.poi.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "route_info",
    indexes = {
      @Index(name = "idx_route_from_place", columnList = "from_place_id"),
      @Index(name = "idx_route_to_place", columnList = "to_place_id"),
      @Index(name = "idx_route_transport_mode", columnList = "transport_mode_id")
    })
@Check(name = "chk_route_different_places", constraints = "((from_place_id <> to_place_id))")
@Check(name = "chk_route_distance", constraints = "((distance_meters >= (0)::double precision))")
@Check(name = "chk_route_travel_time", constraints = "((travel_time_minutes >= 0))")
@Getter
@Setter
public class RouteInfo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "route_id")
  private Long routeId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "from_place_id", nullable = false)
  private Place fromPlace;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "to_place_id", nullable = false)
  private Place toPlace;

  @Column(name = "distance_meters")
  private Double distanceMeters;

  @Column(name = "travel_time_minutes")
  private Integer travelTimeMinutes;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transport_mode_id")
  private TransportMode transportMode;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "route_data", columnDefinition = "jsonb")
  private String routeData;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
