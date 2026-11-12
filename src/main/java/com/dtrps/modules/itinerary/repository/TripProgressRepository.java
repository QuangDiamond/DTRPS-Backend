
package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.TripProgress;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy xuất tiến độ của toàn bộ lịch trình.
 * Mỗi Itinerary chỉ có một TripProgress (itinerary_id UNIQUE).
 */
public interface TripProgressRepository extends JpaRepository<TripProgress, Long> {

    // Lấy tiến độ của một phiên bản lịch trình.
    Optional<TripProgress> findByItineraryItineraryId(Long itineraryId);

    // Khóa bản ghi tiến độ đã tồn tại khi bắt đầu hoặc hoàn thành chuyến đi.
    // Phải gọi trong @Transactional.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
      SELECT tp
      FROM TripProgress tp
      WHERE tp.itinerary.itineraryId = :itineraryId
      """)
    Optional<TripProgress> lockByItineraryId(
            @Param("itineraryId") Long itineraryId);

    // Lấy tiến độ của tất cả phiên bản lịch trình thuộc một Travel Plan.
    List<TripProgress> findByItineraryPlanPlanIdOrderByProgressIdAsc(Long planId);
}
