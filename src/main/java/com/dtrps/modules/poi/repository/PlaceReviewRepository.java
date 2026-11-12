package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.PlaceReview;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceReviewRepository extends JpaRepository<PlaceReview, Long> {
  /** [placeId, average rating, review count] over VISIBLE reviews only */
  @Query(
      "select r.place.placeId, avg(r.rating), count(r) from PlaceReview r"
          + " where r.place.placeId in :placeIds and r.status = 'VISIBLE'"
          + " group by r.place.placeId")
  List<Object[]> ratingsFor(@Param("placeIds") Collection<Long> placeIds);

  @EntityGraph(attributePaths = "user")
  Page<PlaceReview> findByPlacePlaceIdAndStatusOrderByCreatedAtDesc(
      Long placeId, String status, Pageable pageable);

  Optional<PlaceReview> findByPlacePlaceIdAndUserUserId(Long placeId, Long userId);

  boolean existsByUserUserIdAndPlacePlaceId(Long userId, Long placeId);
}
