package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.PlanInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanInterestRepository extends JpaRepository<PlanInterest, Long> {

  /**
   * Sở thích của chuyến được lưu ở travel_plans.category_codes, không dùng bảng này nữa. Chỉ còn
   * xóa theo kế hoạch để dọn các dòng cũ khi xóa kế hoạch (bảng không có ON DELETE CASCADE).
   */
  @Modifying(flushAutomatically = true)
  @Query("DELETE FROM PlanInterest pi WHERE pi.plan.planId = :planId")
  int deleteByPlanId(@Param("planId") Long planId);
}
