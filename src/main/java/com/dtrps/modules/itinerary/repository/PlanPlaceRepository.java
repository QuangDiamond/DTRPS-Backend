package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.PlanPlace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPlaceRepository extends JpaRepository<PlanPlace, Long> {
    @EntityGraph(attributePaths = {"place", "place.category", "place.province"})
    List<PlanPlace> findByPlanPlanIdOrderByPriorityDescPlanPlaceIdAsc(Long planId);

    Optional<PlanPlace> findByPlanPlanIdAndPlacePlaceId(Long planId, Long placeId);

    boolean existsByPlanPlanIdAndPlacePlaceId(Long planId, Long placeId);

    long countByPlanPlanId(Long planId);

    /** Đã từng chọn địa điểm này trong bất kỳ kế hoạch nào của khách (không phân biệt trạng thái). */
    boolean existsByPlanUserUserIdAndPlacePlaceId(Long userId, Long placeId);
}