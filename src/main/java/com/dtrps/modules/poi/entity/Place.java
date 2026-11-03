package com.dtrps.modules.poi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "places",
    indexes = {
      @Index(name = "idx_places_category", columnList = "category_id"),
      @Index(name = "idx_places_province_id", columnList = "province_id")
    })
@Check(
    name = "chk_place_latitude",
    constraints =
        "(((latitude >= ('-90'::integer)::double precision) AND (latitude <= (90)::double precision)))")
@Check(
    name = "chk_place_longitude",
    constraints =
        "(((longitude >= ('-180'::integer)::double precision) AND (longitude <= (180)::double precision)))")
@Check(
    name = "chk_place_status",
    constraints = "status IN ('DRAFT', 'ACTIVE', 'MAINTENANCE', 'PAUSED')")
@Getter
@Setter
public class Place {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "place_id")
  private Long placeId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private PlaceCategory category;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "address", columnDefinition = "text")
  private String address;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "province_id")
  private Province province;

  @Column(name = "external_place_id", length = 255)
  private String externalPlaceId;

  @Column(name = "place_type", length = 50)
  private String placeType;

  @Column(name = "phone", length = 20)
  private String phone;

  @Column(name = "website", columnDefinition = "text")
  private String website;

  /**
   * Category-specific details: amenities, services, booking link, visit rules, best time, tags...
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attributes", columnDefinition = "jsonb")
  private String attributes;

  /** Traveller interests this place suits, matched against user preferences */
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "place_interests",
      indexes = @Index(name = "idx_place_interests_interest", columnList = "interest_id"),
      joinColumns = @JoinColumn(name = "place_id"),
      inverseJoinColumns = @JoinColumn(name = "interest_id"))
  private Set<Interest> interests = new HashSet<>();

  @ColumnDefault("'ACTIVE'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "ACTIVE";

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
