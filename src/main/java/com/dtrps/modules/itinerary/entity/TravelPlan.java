package com.dtrps.modules.itinerary.entity;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "travel_plans",
    indexes = {
      @Index(name = "idx_travel_plans_user_created", columnList = "user_id, created_at DESC")
    })
@Check(name = "chk_plan_budget", constraints = "(((budget IS NULL) OR (budget >= (0)::numeric)))")
@Check(name = "chk_plan_dates", constraints = "((end_date >= start_date))")
@Check(name = "chk_plan_group_size", constraints = "((group_size > 0))")
@Check(
    name = "chk_travel_plan_status",
    constraints = "status IN ('DRAFT', 'PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')")
@Getter
@Setter
public class TravelPlan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "plan_id")
  private Long planId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "destination", nullable = false, length = 255)
  private String destination;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "budget", precision = 15, scale = 2)
  private BigDecimal budget;

  @ColumnDefault("'VND'")
  @Column(name = "currency", nullable = false, length = 3)
  private String currency = "VND";

  @ColumnDefault("1")
  @Column(name = "group_size", nullable = false)
  private Integer groupSize = 1;

  @Column(name = "description", columnDefinition = "text")
  private String description;

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
