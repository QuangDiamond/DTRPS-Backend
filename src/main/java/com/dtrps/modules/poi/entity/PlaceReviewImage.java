package com.dtrps.modules.poi.entity;

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
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

/** A photo a traveller attached to their {@link PlaceReview}, stored on Cloudinary. */
@Entity
@Table(
    name = "place_review_images",
    indexes = {@Index(name = "idx_place_review_images_review", columnList = "review_id")})
@Getter
@Setter
public class PlaceReviewImage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "review_image_id")
  private Long reviewImageId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "review_id", nullable = false)
  private PlaceReview review;

  @Column(name = "image_url", nullable = false, columnDefinition = "text")
  private String imageUrl;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
