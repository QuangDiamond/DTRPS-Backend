
package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.TravelPlan;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelPlanRepository
        extends JpaRepository<TravelPlan, Long> {

    // Task 45: Khóa kế hoạch khi tạo phiên bản lịch trình
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM TravelPlan p WHERE p.planId = :planId")
    Optional<TravelPlan> lockById(
            @Param("planId") Long planId
    );

    // Lấy các kế hoạch thuộc tài khoản đang đăng nhập
    List<TravelPlan> findByUserUserIdOrderByCreatedAtDesc(
            Long userId
    );

    // Chỉ tìm kế hoạch khi đúng ID và chủ sở hữu
    Optional<TravelPlan> findByPlanIdAndUserUserId(
            Long planId,
            Long userId
    );
}
