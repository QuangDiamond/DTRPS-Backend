package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.PlaceReviewImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceReviewImageRepository extends JpaRepository<PlaceReviewImage, Long> {
  List<PlaceReviewImage> findByReviewReviewIdOrderByCreatedAtAsc(Long reviewId);

  @Query(
      "select i from PlaceReviewImage i where i.review.reviewId in :reviewIds"
          + " order by i.createdAt asc")
  List<PlaceReviewImage> findForReviews(@Param("reviewIds") Collection<Long> reviewIds);
}
