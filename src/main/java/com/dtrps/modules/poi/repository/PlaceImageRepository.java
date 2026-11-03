package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.PlaceImage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

  Optional<PlaceImage> findByImageIdAndPlacePlaceId(Long imageId, Long placeId);

  long countByPlacePlaceId(Long placeId);

  boolean existsByPlacePlaceIdAndImageUrl(Long placeId, String imageUrl);

  /** Earliest remaining photo, promoted to cover when the cover is deleted */
  Optional<PlaceImage> findFirstByPlacePlaceIdOrderByImageIdAsc(Long placeId);

  /** Clears the cover flag so a new cover can be set (uq_place_primary_image allows one) */
  @Modifying(flushAutomatically = true)
  @Query(
      "update PlaceImage i set i.isPrimary = false"
          + " where i.place.placeId = :placeId and i.isPrimary = true")
  void clearPrimary(@Param("placeId") Long placeId);
}
