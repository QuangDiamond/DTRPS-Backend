package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, Long> {}
