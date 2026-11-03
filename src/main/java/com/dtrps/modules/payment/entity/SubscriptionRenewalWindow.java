package com.dtrps.modules.payment.entity;

import com.dtrps.modules.user.entity.Notification;
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
    name = "subscription_renewal_windows",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_renewal_window_subscription",
          columnNames = {"window_id", "subscription_id"})
    },
    indexes = {
      @Index(name = "idx_renewal_windows_notification", columnList = "notification_id"),
      @Index(name = "idx_renewal_windows_subscription", columnList = "subscription_id")
    })
@Check(name = "chk_renewal_window_dates", constraints = "((closes_at > opens_at))")
@Check(
    name = "chk_renewal_window_status",
    constraints = "status IN ('OPEN', 'USED', 'EXPIRED', 'CANCELLED')")
@Getter
@Setter
public class SubscriptionRenewalWindow {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "window_id")
  private Long windowId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  private Subscription subscription;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "notification_id")
  private Notification notification;

  @Column(name = "opens_at", nullable = false)
  private Instant opensAt;

  @Column(name = "closes_at", nullable = false)
  private Instant closesAt;

  @ColumnDefault("'OPEN'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "OPEN";

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
