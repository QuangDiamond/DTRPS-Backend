package com.dtrps.modules.businessowner.entity;

import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.Province;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "business_listing_revisions",
    indexes = {
      @Index(name = "idx_listing_revisions_category", columnList = "category_id"),
      @Index(name = "idx_listing_revisions_listing", columnList = "listing_id"),
      @Index(name = "idx_listing_revisions_proposer", columnList = "proposed_by"),
      @Index(name = "idx_listing_revisions_province", columnList = "province_id"),
      @Index(name = "idx_listing_revisions_reviewer", columnList = "reviewed_by")
    })
@Check(
    name = "chk_listing_revision_latitude",
    constraints =
        "(((latitude >= ('-90'::integer)::double precision) AND (latitude <= (90)::double precision)))")
@Check(
    name = "chk_listing_revision_longitude",
    constraints =
        "(((longitude >= ('-180'::integer)::double precision) AND (longitude <= (180)::double precision)))")
@Check(
    name = "chk_listing_revision_status",
    constraints = "status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')")
@Check(
    name = "chk_listing_revision_reject_reason",
    constraints =
        "reject_reason_code IS NULL"
            + " OR reject_reason_code IN ('pricing', 'location', 'photos', 'duplicate', 'other')")
@Getter
@Setter
public class BusinessListingRevision {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "revision_id")
  private Long revisionId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "listing_id", nullable = false)
  private BusinessListing listing;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "proposed_by", nullable = false)
  private User proposedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private PlaceCategory category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "province_id")
  private Province province;

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

  /**
   * Full proposed content (contact, hours, prices, photos, attributes), applied to the place on
   * approval
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  private String payload;

  /** PENDING until reviewed; CANCELLED when the business owner withdraws it before review */
  @ColumnDefault("'PENDING'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "PENDING";

  @Column(name = "reject_reason_code", length = 30)
  private String rejectReasonCode;

  /** Feedback sent to the business owner */
  @Column(name = "partner_feedback", columnDefinition = "text")
  private String partnerFeedback;

  /** Internal note, visible to admins only */
  @Column(name = "review_note", columnDefinition = "text")
  private String reviewNote;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewed_by")
  private User reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
