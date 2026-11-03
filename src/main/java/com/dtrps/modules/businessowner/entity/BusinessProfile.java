package com.dtrps.modules.businessowner.entity;

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
    name = "business_profiles",
    indexes = {@Index(name = "idx_business_profiles_approver", columnList = "approved_by")})
@Check(
    name = "chk_business_profile_status",
    constraints = "status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'INACTIVE')")
@Getter
@Setter
public class BusinessProfile {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "business_id")
  private Long businessId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "business_name", nullable = false, length = 255)
  private String businessName;

  @Column(name = "tax_code", unique = true, length = 20)
  private String taxCode;

  @Column(name = "contact_email", length = 255)
  private String contactEmail;

  @Column(name = "contact_phone", length = 20)
  private String contactPhone;

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
