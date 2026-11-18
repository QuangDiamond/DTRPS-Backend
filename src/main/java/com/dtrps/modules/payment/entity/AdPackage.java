package com.dtrps.modules.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Gói quảng cáo trong bảng giá: giá, số ngày và số địa điểm được quảng cáo cùng lúc. */
@Entity
@Table(name = "ad_packages")
@Check(name = "chk_ad_package_price", constraints = "price > 0")
@Check(name = "chk_ad_package_duration", constraints = "duration_days > 0")
@Check(name = "chk_ad_package_places", constraints = "max_places > 0")
@Check(name = "chk_ad_package_status", constraints = "status IN ('ACTIVE', 'INACTIVE')")
@Getter
@Setter
public class AdPackage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "package_id")
  private Long packageId;

  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "price", nullable = false, precision = 15, scale = 2)
  private BigDecimal price;

  @Column(name = "duration_days", nullable = false)
  private Integer durationDays;

  @Column(name = "max_places", nullable = false)
  private Integer maxPlaces;

  @ColumnDefault("'ACTIVE'")
  @Column(name = "status", nullable = false, length = 20)
  private String status = "ACTIVE";

  @ColumnDefault("0")
  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
