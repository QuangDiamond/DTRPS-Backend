
package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.TravelPlan;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    // ---- Xóa kế hoạch ----
    // DB không có ON DELETE CASCADE, nên xóa dữ liệu con theo thứ tự từ dưới lên
    // trong cùng một transaction (gọi từ TravelPlanService.delete).

    @Modifying(flushAutomatically = true)
    @Query("""
      DELETE FROM ActivityProgress a
      WHERE a.progress.progressId IN (
          SELECT p.progressId FROM TripProgress p WHERE p.itinerary.plan.planId = :planId)
        OR a.item.itemId IN (
          SELECT i.itemId FROM ItineraryItem i WHERE i.day.itinerary.plan.planId = :planId)
      """)
    int deleteActivityProgressByPlanId(@Param("planId") Long planId);

    @Modifying
    @Query("""
      DELETE FROM TripProgress p
      WHERE p.itinerary.itineraryId IN (
          SELECT it.itineraryId FROM Itinerary it WHERE it.plan.planId = :planId)
      """)
    int deleteTripProgressByPlanId(@Param("planId") Long planId);

    @Modifying
    @Query("""
      DELETE FROM ItineraryShare s
      WHERE s.itinerary.itineraryId IN (
          SELECT it.itineraryId FROM Itinerary it WHERE it.plan.planId = :planId)
      """)
    int deleteItinerarySharesByPlanId(@Param("planId") Long planId);

    @Modifying
    @Query("""
      DELETE FROM ItineraryItem i
      WHERE i.day.dayId IN (
          SELECT d.dayId FROM ItineraryDay d WHERE d.itinerary.plan.planId = :planId)
      """)
    int deleteItineraryItemsByPlanId(@Param("planId") Long planId);

    @Modifying
    @Query("""
      DELETE FROM ItineraryDay d
      WHERE d.itinerary.itineraryId IN (
          SELECT it.itineraryId FROM Itinerary it WHERE it.plan.planId = :planId)
      """)
    int deleteItineraryDaysByPlanId(@Param("planId") Long planId);

    @Modifying
    @Query("DELETE FROM Itinerary it WHERE it.plan.planId = :planId")
    int deleteItinerariesByPlanId(@Param("planId") Long planId);

    @Modifying
    @Query("DELETE FROM PlanPlace pp WHERE pp.plan.planId = :planId")
    int deletePlanPlacesByPlanId(@Param("planId") Long planId);

    @Modifying
    @Query("DELETE FROM TripFeedback f WHERE f.plan.planId = :planId")
    int deleteTripFeedbackByPlanId(@Param("planId") Long planId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM TravelPlan p WHERE p.planId = :planId")
    int deletePlanById(@Param("planId") Long planId);
}
