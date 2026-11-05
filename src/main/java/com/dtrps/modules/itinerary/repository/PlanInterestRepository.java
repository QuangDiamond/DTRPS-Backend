package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.PlanInterest;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanInterestRepository extends JpaRepository<PlanInterest, Long> {

  /** Sở thích của nhiều kế hoạch trong một truy vấn, tránh N+1 khi trả danh sách. */
  @Query(
      """
      SELECT pi FROM PlanInterest pi
      JOIN FETCH pi.interest i
      WHERE pi.plan.planId IN :planIds
      ORDER BY i.interestId ASC
      """)
  List<PlanInterest> findWithInterestByPlanIds(@Param("planIds") Collection<Long> planIds);

  @Modifying(flushAutomatically = true)
  @Query("DELETE FROM PlanInterest pi WHERE pi.plan.planId = :planId")
  int deleteByPlanId(@Param("planId") Long planId);
}
