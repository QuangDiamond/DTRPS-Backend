package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.TripFeedback;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TripFeedbackRepository
    extends JpaRepository<TripFeedback, Long>, JpaSpecificationExecutor<TripFeedback> {
  Optional<TripFeedback> findByPlanPlanIdAndUserUserId(Long planId, Long userId);

  boolean existsByPlanPlanIdAndUserUserId(Long planId, Long userId);

  // ===== Admin =====

  /** Danh sách quản lý đánh giá chuyến đi: kèm người viết + kế hoạch trong cùng truy vấn. */
  @Override
  @EntityGraph(attributePaths = {"user", "plan"})
  Page<TripFeedback> findAll(Specification<TripFeedback> spec, Pageable pageable);

  @Query("select avg(f.rating) from TripFeedback f")
  Double averageRating();
}
