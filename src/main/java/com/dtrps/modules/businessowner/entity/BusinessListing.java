package com.dtrps.modules.businessowner.entity;

import com.dtrps.modules.poi.entity.Place;
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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "business_listings",
    indexes = {@Index(name = "idx_business_listings_approver", columnList = "approved_by")})
@Check(
    name = "chk_business_listing_status",
    constraints = "status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')")
@Getter
@Setter
public class BusinessListing {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "listing_id")
  private Long listingId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false, unique = true)
  private BusinessProfile business;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "place_id", nullable = false, unique = true)
  private Place place;

  @ColumnDefault("'PENDING'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "PENDING";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "approved_by")
  private User approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
