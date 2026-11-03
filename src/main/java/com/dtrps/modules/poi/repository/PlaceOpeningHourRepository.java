package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.PlaceOpeningHour;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceOpeningHourRepository extends JpaRepository<PlaceOpeningHour, Long> {
  List<PlaceOpeningHour> findByPlacePlaceIdOrderByDayOfWeekAsc(Long placeId);

  @Modifying(flushAutomatically = true)
  @Query("delete from PlaceOpeningHour h where h.place.placeId = :placeId")
  void deleteByPlaceId(@Param("placeId") Long placeId);
}
