package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.PlaceImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceImageRepository extends JpaRepository<PlaceImage, Long> {
  /** Cover first, then in upload order */
  List<PlaceImage> findByPlacePlaceIdOrderByIsPrimaryDescImageIdAsc(Long placeId);

  /** Images of several places at once, cover first within each place */
  @Query(
      "select i from PlaceImage i where i.place.placeId in :placeIds"
          + " order by i.isPrimary desc, i.imageId asc")
  List<PlaceImage> findForPlaces(@Param("placeIds") Collection<Long> placeIds);

  @Modifying(flushAutomatically = true)
  @Query("delete from PlaceImage i where i.place.placeId = :placeId")
  void deleteByPlaceId(@Param("placeId") Long placeId);
}
