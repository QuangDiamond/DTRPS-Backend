package com.dtrps.modules.payment.entity;

import com.dtrps.modules.businessowner.entity.BusinessProfile;
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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "subscriptions",
    indexes = {@Index(name = "idx_subscriptions_business", columnList = "business_id")})
@Check(name = "chk_subscription_dates", constraints = "((end_date > start_date))")
@Check(name = "chk_subscription_fee", constraints = "((monthly_fee >= (0)::numeric))")
@Check(
    name = "chk_subscription_status",
    constraints = "status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED')")
@Getter
@Setter
public class Subscription {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "subscription_id")
  private Long subscriptionId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "business_id", nullable = false)
  private BusinessProfile business;

  @Column(name = "start_date", nullable = false)
  private Instant startDate;

  @Column(name = "end_date", nullable = false)
  private Instant endDate;

  @ColumnDefault("'PENDING'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "PENDING";

  @Column(name = "monthly_fee", nullable = false, precision = 15, scale = 2)
  private BigDecimal monthlyFee;

  @ColumnDefault("'VND'")
  @Column(name = "currency", nullable = false, length = 3)
  private String currency = "VND";

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
