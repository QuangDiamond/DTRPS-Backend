package com.dtrps.modules.advertisement.entity;

import com.dtrps.modules.businessowner.entity.BusinessListing;
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
    name = "advertisements",
    indexes = {@Index(name = "idx_advertisements_listing", columnList = "listing_id")})
@Check(
    name = "chk_advertisement_dates",
    constraints = "(((start_at IS NULL) OR (end_at IS NULL) OR (end_at > start_at)))")
@Check(
    name = "chk_advertisement_status",
    constraints =
        "status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'PUBLISHED', 'EXPIRED', 'SUSPENDED')")
@Getter
@Setter
public class Advertisement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "advertisement_id")
  private Long advertisementId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "listing_id", nullable = false)
  private BusinessListing listing;

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

  @ColumnDefault("'DRAFT'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "DRAFT";

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
