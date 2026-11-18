package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.PlacePrice;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacePriceRepository extends JpaRepository<PlacePrice, Long> {
  List<PlacePrice> findByPlacePlaceIdOrderByPriceIdAsc(Long placeId);

  /** Giá của nhiều địa điểm trong một truy vấn (tránh N+1). */
  List<PlacePrice> findByPlacePlaceIdInOrderByPriceIdAsc(Collection<Long> placeIds);

  @Modifying(flushAutomatically = true)
  @Query("delete from PlacePrice p where p.place.placeId = :placeId")
  void deleteByPlaceId(@Param("placeId") Long placeId);

  Optional<PlacePrice> findByPriceIdAndPlacePlaceId(Long priceId, Long placeId);

  long countByPlacePlaceId(Long placeId);
}
