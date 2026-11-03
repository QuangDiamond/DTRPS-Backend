package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.TripProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripProgressRepository extends JpaRepository<TripProgress, Long> {}
