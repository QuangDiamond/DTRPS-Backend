
package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.ItineraryDay;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItineraryDayRepository
        extends JpaRepository<ItineraryDay, Long> {

    // Task 45: Lấy các ngày theo thứ tự
    List<ItineraryDay>
    findByItineraryItineraryIdOrderByDayNumberAsc(
            Long itineraryId
    );

    // Task 46: Khóa một ngày trước khi sửa thứ tự hoạt động
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ItineraryDay d WHERE d.dayId = :dayId")
    Optional<ItineraryDay> lockByDayId(
            @Param("dayId") Long dayId
    );
}
