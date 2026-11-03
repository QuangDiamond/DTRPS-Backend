package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {}
