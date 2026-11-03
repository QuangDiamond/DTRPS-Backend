package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.PlacePrice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacePriceRepository extends JpaRepository<PlacePrice, Long> {
  List<PlacePrice> findByPlacePlaceIdOrderByPriceIdAsc(Long placeId);

  @Modifying(flushAutomatically = true)
  @Query("delete from PlacePrice p where p.place.placeId = :placeId")
  void deleteByPlaceId(@Param("placeId") Long placeId);
}
