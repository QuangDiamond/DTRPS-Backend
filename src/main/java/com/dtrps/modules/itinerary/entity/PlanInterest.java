package com.dtrps.modules.itinerary.entity;

import com.dtrps.modules.poi.entity.Interest;
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
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "plan_interests",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_plan_interest",
          columnNames = {"plan_id", "interest_id"})
    },
    indexes = {@Index(name = "idx_plan_interests_interest", columnList = "interest_id")})
@Getter
@Setter
public class PlanInterest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "plan_interest_id")
  private Long planInterestId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private TravelPlan plan;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "interest_id", nullable = false)
  private Interest interest;
}
