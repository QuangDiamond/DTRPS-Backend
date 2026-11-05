package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItineraryRepository
        extends JpaRepository<Itinerary, Long> {

    // Lấy phiên bản mới nhất của kế hoạch
    Optional<Itinerary>
    findTopByPlanPlanIdOrderByVersionNumberDesc(
            Long planId
    );

    // Lấy lịch trình ACTIVE mới nhất
    Optional<Itinerary>
    findFirstByPlanPlanIdAndStatusOrderByVersionNumberDesc(
            Long planId,
            String status
    );

    // Lấy tất cả lịch trình theo trạng thái
    List<Itinerary> findByPlanPlanIdAndStatus(
            Long planId,
            String status
    );
}