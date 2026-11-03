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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "trip_feedback",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_trip_feedback_user_plan",
          columnNames = {"plan_id", "user_id"})
    },
    indexes = {@Index(name = "idx_trip_feedback_user", columnList = "user_id")})
@Check(name = "chk_trip_feedback_rating", constraints = "(((rating >= 1) AND (rating <= 5)))")
@Getter
@Setter
public class TripFeedback {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "feedback_id")
  private Long feedbackId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private TravelPlan plan;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "rating", nullable = false)
  private Short rating;

  @Column(name = "comment", columnDefinition = "text")
  private String comment;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
