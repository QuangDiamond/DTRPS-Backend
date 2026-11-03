package com.dtrps.modules.poi.entity;

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

@Entity
@Table(
    name = "place_reviews",
    indexes = {
      @Index(name = "idx_place_reviews_place", columnList = "place_id"),
      @Index(name = "idx_place_reviews_user", columnList = "user_id")
    })
@Check(name = "chk_place_rating", constraints = "(((rating >= 1) AND (rating <= 5)))")
@Check(name = "chk_place_review_status", constraints = "status IN ('VISIBLE', 'HIDDEN')")
@Getter
@Setter
public class PlaceReview {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "review_id")
  private Long reviewId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "place_id", nullable = false)
  private Place place;

  @Column(name = "rating", nullable = false)
  private Short rating;

  @Column(name = "comment", columnDefinition = "text")
  private String comment;

  @ColumnDefault("'VISIBLE'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "VISIBLE";

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
