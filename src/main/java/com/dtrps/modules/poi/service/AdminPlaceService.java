package com.dtrps.modules.poi.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.businessowner.repository.BusinessListingRepository;
import com.dtrps.modules.poi.dto.AdminPlaceDetail;
import com.dtrps.modules.poi.dto.AdminPlaceStats;
import com.dtrps.modules.poi.dto.AdminPlaceSummary;
import com.dtrps.modules.poi.dto.PlaceDetails;
import com.dtrps.modules.poi.dto.ReferenceItem;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin catalogue of every place: platform POIs and partner places alike. Read-only for now —
 * partner places change through listing review and revisions.
 */
@Service
public class AdminPlaceService {
  public static final String SOURCE_PARTNER = "PARTNER";
  public static final String SOURCE_ADMIN = "ADMIN";
  static final String PLACE_NOT_FOUND = "Không tìm thấy địa điểm";
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

  private record Rating(Double average, long count) {}

  private final PlaceRepository placeRepository;
  private final PlaceImageRepository imageRepository;
  private final PlaceReviewRepository reviewRepository;
  private final BusinessListingRepository listingRepository;
  private final PlaceDetailsService placeDetails;
  private final Clock clock;

  public AdminPlaceService(
      PlaceRepository placeRepository,
      PlaceImageRepository imageRepository,
      PlaceReviewRepository reviewRepository,
      BusinessListingRepository listingRepository,
      PlaceDetailsService placeDetails,
      Clock clock) {
    this.placeRepository = placeRepository;
    this.imageRepository = imageRepository;
    this.reviewRepository = reviewRepository;
    this.listingRepository = listingRepository;
    this.placeDetails = placeDetails;
    this.clock = clock;
  }

  /**
   * Filters are optional. {@code group}/{@code province} are reference codes, {@code status} a
   * place status, {@code source} PARTNER or ADMIN.
   */
  @Transactional(readOnly = true)
  public PageResponse<AdminPlaceSummary> list(
      String keyword,
      String group,
      String province,
      String status,
      String source,
      Pageable pageable) {
    Specification<Place> spec = (root, query, cb) -> cb.conjunction();
    if (hasText(keyword)) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.or(
                      cb.like(cb.lower(root.get("name")), like),
                      cb.like(cb.lower(root.get("address")), like)));
    }
    if (hasText(group)) {
      spec =
          spec.and(
              (root, query, cb) -> {
                Join<Place, PlaceCategory> category = root.join("category");
                return cb.equal(category.get("parent").get("code"), group.trim());
              });
    }
    if (hasText(province)) {
      spec =
          spec.and(
              (root, query, cb) -> cb.equal(root.get("province").get("code"), province.trim()));
    }
    if (hasText(status)) {
      String wanted = status.trim().toUpperCase(Locale.ROOT);
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), wanted));
    }
    if (hasText(source)) {
      boolean partner = SOURCE_PARTNER.equalsIgnoreCase(source.trim());
      spec =
          spec.and(
              (root, query, cb) -> {
                Subquery<Long> owned = query.subquery(Long.class);
                var listing = owned.from(BusinessListing.class);
                owned.select(listing.get("listingId")).where(cb.equal(listing.get("place"), root));
                return partner ? cb.exists(owned) : cb.not(cb.exists(owned));
              });
    }
    Page<Place> page = placeRepository.findAll(spec, pageable);
    List<Long> ids = page.getContent().stream().map(Place::getPlaceId).toList();
    Map<Long, BusinessListing> listings = listingsOf(ids);
    Map<Long, List<PlaceImage>> images = imagesOf(ids);
    Map<Long, Rating> ratings = ratingsOf(ids);
    return PageResponse.from(
        page.map(
            place ->
                summary(
                    place,
                    listings.get(place.getPlaceId()),
                    images.getOrDefault(place.getPlaceId(), List.of()),
                    ratings.get(place.getPlaceId()))));
  }

  @Transactional(readOnly = true)
  public AdminPlaceDetail get(Long placeId) {
    Place place =
        placeRepository
            .findWithDetailsByPlaceId(placeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PLACE_NOT_FOUND));
    List<Long> ids = List.of(placeId);
    PlaceDetails details = placeDetails.read(placeId);
    return new AdminPlaceDetail(
        summary(
            place,
            listingsOf(ids).get(placeId),
            imagesOf(ids).getOrDefault(placeId, List.of()),
            ratingsOf(ids).get(placeId)),
        place.getDescription(),
        place.getLatitude(),
        place.getLongitude(),
        place.getPhone(),
        place.getWebsite(),
        details.openingHours(),
        details.prices(),
        details.images());
  }

  /** "This month" follows Vietnam time, where the platform operates. */
  @Transactional(readOnly = true)
  public AdminPlaceStats stats() {
    Instant monthStart =
        LocalDate.now(clock.withZone(VIETNAM)).withDayOfMonth(1).atStartOfDay(VIETNAM).toInstant();
    long total = placeRepository.count();
    long partner = listingRepository.count();
    Map<String, Long> byGroup = new LinkedHashMap<>();
    for (Object[] row : placeRepository.countByGroup()) {
      byGroup.put((String) row[0], ((Number) row[1]).longValue());
    }
    return new AdminPlaceStats(
        total,
        placeRepository.countByStatus("ACTIVE"),
        placeRepository.countByStatus("DRAFT"),
        placeRepository.countByStatus("PAUSED") + placeRepository.countByStatus("MAINTENANCE"),
        partner,
        total - partner,
        placeRepository.countByCreatedAtGreaterThanEqual(monthStart),
        byGroup);
  }

  // ===== Helpers =====

  private Map<Long, BusinessListing> listingsOf(List<Long> placeIds) {
    if (placeIds.isEmpty()) {
      return Map.of();
    }
    return listingRepository.findByPlacePlaceIdIn(placeIds).stream()
        .collect(Collectors.toMap(l -> l.getPlace().getPlaceId(), Function.identity()));
  }

  private Map<Long, List<PlaceImage>> imagesOf(List<Long> placeIds) {
    if (placeIds.isEmpty()) {
      return Map.of();
    }
    return imageRepository.findForPlaces(placeIds).stream()
        .collect(Collectors.groupingBy(i -> i.getPlace().getPlaceId()));
  }

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

  private static AdminPlaceSummary summary(
      Place place, BusinessListing listing, List<PlaceImage> images, Rating rating) {
    PlaceCategory category = place.getCategory();
    PlaceCategory group = category == null ? null : category.getParent();
    return new AdminPlaceSummary(
        place.getPlaceId(),
        place.getName(),
        group == null ? null : new ReferenceItem(group.getCode(), group.getName()),
        category == null ? null : new ReferenceItem(category.getCode(), category.getName()),
        place.getProvince() == null
            ? null
            : new ReferenceItem(place.getProvince().getCode(), place.getProvince().getName()),
        place.getAddress(),
        place.getStatus(),
        listing == null ? SOURCE_ADMIN : SOURCE_PARTNER,
        listing == null ? null : listing.getListingId(),
        listing == null ? null : listing.getStatus(),
        listing == null ? null : listing.getBusiness().getBusinessName(),
        images.isEmpty() ? null : images.get(0).getImageUrl(),
        images.size(),
        rating == null ? null : rating.average(),
        rating == null ? 0 : rating.count(),
        place.getCreatedAt(),
        place.getUpdatedAt());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
