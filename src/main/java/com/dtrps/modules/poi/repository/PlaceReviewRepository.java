package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.PlaceReview;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceReviewRepository
    extends JpaRepository<PlaceReview, Long>, JpaSpecificationExecutor<PlaceReview> {
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

  // ===== Admin =====

  /** Danh sách quản lý đánh giá: kèm người viết + địa điểm trong cùng truy vấn. */
  @Override
  @EntityGraph(attributePaths = {"user", "place"})
  Page<PlaceReview> findAll(Specification<PlaceReview> spec, Pageable pageable);

  long countByStatus(String status);

  @EntityGraph(attributePaths = {"user", "place"})
  List<PlaceReview> findByReviewIdIn(Collection<Long> reviewIds);

  @Query("select avg(r.rating) from PlaceReview r")
  Double averageRating();

  long countByRatingLessThanEqualAndCreatedAtAfter(Short rating, Instant after);
}
