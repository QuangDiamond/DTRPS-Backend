package com.dtrps.modules.payment.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "payments",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_payment_transaction",
          columnNames = {"payment_gateway", "transaction_reference"})
    },
    indexes = {
      @Index(
          name = "idx_payments_renewal_window",
          columnList = "renewal_window_id, subscription_id"),
      @Index(name = "idx_payments_subscription", columnList = "subscription_id"),
      @Index(name = "idx_payments_user", columnList = "user_id")
    })
@Check(name = "chk_payment_amount", constraints = "((amount > (0)::numeric))")
@Check(
    name = "chk_payment_renewal_window",
    constraints = "(((payment_type <> 'RENEWAL') OR (renewal_window_id IS NOT NULL)))")
@Check(
    name = "chk_payment_status",
    constraints = "status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED')")
@Check(name = "chk_payment_type", constraints = "payment_type IN ('INITIAL', 'RENEWAL')")
@Getter
@Setter
public class Payment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "payment_id")
  private Long paymentId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  private Subscription subscription;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @ColumnDefault("'VND'")
  @Column(name = "currency", nullable = false, length = 3)
  private String currency = "VND";

  @Column(name = "payment_method", length = 50)
  private String paymentMethod;

  @Column(name = "payment_gateway", length = 50)
  private String paymentGateway;

  @Column(name = "transaction_reference", length = 255)
  private String transactionReference;

  @Column(name = "payment_type", nullable = false, length = 30)
  private String paymentType;

  @ColumnDefault("'PENDING'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "PENDING";

  @Column(name = "paid_at")
  private Instant paidAt;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "renewal_window_id")
  private SubscriptionRenewalWindow renewalWindow;
}
