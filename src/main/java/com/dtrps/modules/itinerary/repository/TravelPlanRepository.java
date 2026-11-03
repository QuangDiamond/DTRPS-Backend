package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.TravelPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {}
