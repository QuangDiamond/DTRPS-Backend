package com.dtrps.modules.poi.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.poi.dto.PlaceDetailResponse;
import com.dtrps.modules.poi.dto.PlaceSummaryResponse;
import com.dtrps.modules.poi.dto.ReferenceItem;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import jakarta.persistence.criteria.Join;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public catalogue of ACTIVE places: the list travellers browse and the place they open. {@code
 * category} filters on the subtype code (e.g. "hotel"), not the service group.
 */
@Service
public class PlaceService {
  private static final String ACTIVE = "ACTIVE";
  static final String PLACE_NOT_FOUND = "Không tìm thấy địa điểm";

  private record Rating(Double average, long count) {}

  private final PlaceRepository placeRepository;
  private final PlaceImageRepository imageRepository;
  private final PlaceReviewRepository reviewRepository;
  private final PlaceDetailsService placeDetails;

  public PlaceService(
      PlaceRepository placeRepository,
      PlaceImageRepository imageRepository,
      PlaceReviewRepository reviewRepository,
      PlaceDetailsService placeDetails) {
    this.placeRepository = placeRepository;
    this.imageRepository = imageRepository;
    this.reviewRepository = reviewRepository;
    this.placeDetails = placeDetails;
  }

  /** Filters are optional. {@code category} and {@code province} are reference codes. */
  @Transactional(readOnly = true)
  public PageResponse<PlaceSummaryResponse> list(
      String keyword, String category, String province, Pageable pageable) {
    Specification<Place> spec = (root, query, cb) -> cb.equal(root.get("status"), ACTIVE);
    if (hasText(keyword)) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), like));
    }
    if (hasText(category)) {
      String code = category.trim();
      spec =
          spec.and(
              (root, query, cb) -> {
                Join<Place, PlaceCategory> join = root.join("category");
                return cb.equal(join.get("code"), code);
              });
    }
    if (hasText(province)) {
      String code = province.trim();
      spec = spec.and((root, query, cb) -> cb.equal(root.get("province").get("code"), code));
    }
    Page<Place> page = placeRepository.findAll(spec, pageable);
    List<Long> ids = page.getContent().stream().map(Place::getPlaceId).toList();
    Map<Long, List<PlaceImage>> images = imagesOf(ids);
    Map<Long, Rating> ratings = ratingsOf(ids);
    return PageResponse.from(
        page.map(
            place ->
                summary(
                    place,
                    images.getOrDefault(place.getPlaceId(), List.of()),
                    ratings.get(place.getPlaceId()))));
  }

  @Transactional(readOnly = true)
  public PlaceDetailResponse get(Long placeId) {
    Place place =
        placeRepository
            .findWithDetailsByPlaceId(placeId)
            .filter(found -> ACTIVE.equals(found.getStatus()))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PLACE_NOT_FOUND));
    List<Long> ids = List.of(placeId);
    List<PlaceImage> images = imagesOf(ids).getOrDefault(placeId, List.of());
    PlaceSummaryResponse summary = summary(place, images, ratingsOf(ids).get(placeId));
    var details = placeDetails.read(placeId);
    return new PlaceDetailResponse(
        summary,
        place.getDescription(),
        place.getLatitude(),
        place.getLongitude(),
        place.getPhone(),
        place.getWebsite(),
        details.openingHours(),
        details.prices(),
        details.images());
  }

  // ===== Helpers =====

  /** Images of every place on the page in one query, cover first within each place. */
  private Map<Long, List<PlaceImage>> imagesOf(List<Long> placeIds) {
    if (placeIds.isEmpty()) {
      return Map.of();
    }
    return imageRepository.findForPlaces(placeIds).stream()
        .collect(Collectors.groupingBy(i -> i.getPlace().getPlaceId()));
  }

  /** Ratings of every place on the page in one query. */
  private Map<Long, Rating> ratingsOf(List<Long> placeIds) {
    Map<Long, Rating> ratings = new HashMap<>();
    if (placeIds.isEmpty()) {
      return ratings;
    }
    for (Object[] row : reviewRepository.ratingsFor(placeIds)) {
      double average = Math.round(((Number) row[1]).doubleValue() * 10) / 10.0;
      ratings.put((Long) row[0], new Rating(average, ((Number) row[2]).longValue()));
    }
    return ratings;
  }

  private static PlaceSummaryResponse summary(
      Place place, List<PlaceImage> images, Rating rating) {
    PlaceCategory category = place.getCategory();
    PlaceCategory group = category == null ? null : category.getParent();
    return new PlaceSummaryResponse(
        place.getPlaceId(),
        place.getName(),
        group == null ? null : new ReferenceItem(group.getCode(), group.getName()),
        category == null ? null : new ReferenceItem(category.getCode(), category.getName()),
        place.getProvince() == null
            ? null
            : new ReferenceItem(place.getProvince().getCode(), place.getProvince().getName()),
        place.getAddress(),
        images.isEmpty() ? null : images.get(0).getImageUrl(),
        rating == null ? null : rating.average(),
        rating == null ? 0 : rating.count());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
