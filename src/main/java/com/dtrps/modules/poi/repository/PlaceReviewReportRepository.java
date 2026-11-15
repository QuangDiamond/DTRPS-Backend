package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.PlaceReviewReport;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceReviewReportRepository extends JpaRepository<PlaceReviewReport, Long> {
  boolean existsByReviewReviewIdAndReporterUserId(Long reviewId, Long reporterId);

  boolean existsByReviewReviewIdAndStatus(Long reviewId, String status);

  /** Đánh giá ở địa điểm này mà người dùng đã báo cáo */
  @Query(
      "select r.review.reviewId from PlaceReviewReport r"
          + " where r.reporter.userId = :userId and r.review.place.placeId = :placeId")
  List<Long> reviewIdsReportedBy(@Param("userId") Long userId, @Param("placeId") Long placeId);

  @EntityGraph(attributePaths = "reporter")
  List<PlaceReviewReport> findByReviewReviewIdAndStatus(Long reviewId, String status);

  @EntityGraph(attributePaths = "reporter")
  List<PlaceReviewReport> findByReviewReviewIdInAndStatusOrderByCreatedAtDesc(
      Collection<Long> reviewIds, String status);

  /** Số đánh giá đang có báo cáo chờ xử lý (đếm theo đánh giá, không theo lượt) */
  @Query("select count(distinct r.review.reviewId) from PlaceReviewReport r where r.status = 'PENDING'")
  long countPendingReviews();

  /**
   * Đánh giá có báo cáo ở trạng thái {@code status}, gộp theo đánh giá: [reviewId, số lượt, lần báo
   * cáo gần nhất]. {@code placeId} = 0 là mọi địa điểm; {@code reasons} rỗng thì không được gọi.
   */
  @Query(
      value =
          "select r.review.reviewId, count(r), max(r.createdAt) from PlaceReviewReport r"
              + " where r.status = :status and r.reason in :reasons"
              + " and r.createdAt >= :from and r.createdAt < :to"
              + " and (:placeId = 0 or r.review.place.placeId = :placeId)"
              + " group by r.review.reviewId"
              + " order by count(r) desc, max(r.createdAt) desc, r.review.reviewId desc",
      countQuery =
          "select count(distinct r.review.reviewId) from PlaceReviewReport r"
              + " where r.status = :status and r.reason in :reasons"
              + " and r.createdAt >= :from and r.createdAt < :to"
              + " and (:placeId = 0 or r.review.place.placeId = :placeId)")
  Page<Object[]> groupedByMostReports(
      @Param("status") String status,
      @Param("reasons") Collection<String> reasons,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("placeId") long placeId,
      Pageable pageable);

  @Query(
      value =
          "select r.review.reviewId, count(r), max(r.createdAt) from PlaceReviewReport r"
              + " where r.status = :status and r.reason in :reasons"
              + " and r.createdAt >= :from and r.createdAt < :to"
              + " and (:placeId = 0 or r.review.place.placeId = :placeId)"
              + " group by r.review.reviewId"
              + " order by max(r.createdAt) desc, r.review.reviewId desc",
      countQuery =
          "select count(distinct r.review.reviewId) from PlaceReviewReport r"
              + " where r.status = :status and r.reason in :reasons"
              + " and r.createdAt >= :from and r.createdAt < :to"
              + " and (:placeId = 0 or r.review.place.placeId = :placeId)")
  Page<Object[]> groupedByNewest(
      @Param("status") String status,
      @Param("reasons") Collection<String> reasons,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("placeId") long placeId,
      Pageable pageable);
}
