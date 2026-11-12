package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.TripFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripFeedbackRepository extends JpaRepository<TripFeedback, Long> {
  Optional<TripFeedback> findByPlanPlanIdAndUserUserId(Long planId, Long userId);

  boolean existsByPlanPlanIdAndUserUserId(Long planId, Long userId);
}
