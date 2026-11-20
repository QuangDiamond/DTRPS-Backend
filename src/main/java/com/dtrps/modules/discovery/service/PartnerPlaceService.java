package com.dtrps.modules.discovery.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.businessowner.repository.BusinessListingRepository;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Địa điểm do đối tác đã xác minh đăng ký tại điểm đến của kế hoạch. Hiển thị riêng một cột bên
 * cạnh danh sách gợi ý AI, không ảnh hưởng tới điểm số hay thứ hạng gợi ý.
 */
@Service
public class PartnerPlaceService {

  private final TravelPlanRepository plans;
  private final BusinessListingRepository listings;
  private final PlaceImageRepository images;
  private final PlaceReviewRepository reviews;

  public PartnerPlaceService(
      TravelPlanRepository plans,
      BusinessListingRepository listings,
      PlaceImageRepository images,
      PlaceReviewRepository reviews) {
    this.plans = plans;
    this.listings = listings;
    this.images = images;
    this.reviews = reviews;
  }

  @Transactional(readOnly = true)
  public List<PartnerPlace> forPlan(Long userId, Long planId) {
    TravelPlan plan =
        plans
            .findByPlanIdAndUserUserId(planId, userId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy kế hoạch du lịch"));
    String destination =
        plan.getDestination() == null ? "" : plan.getDestination().trim().toLowerCase(Locale.ROOT);
    if (destination.isEmpty()) {
      return List.of();
    }

    // Một địa điểm chỉ hiện một lần dù có nhiều niêm yết.
    Map<Long, BusinessListing> byPlace = new LinkedHashMap<>();
    for (BusinessListing listing : listings.findVerifiedInProvince(destination)) {
      byPlace.putIfAbsent(listing.getPlace().getPlaceId(), listing);
    }
    if (byPlace.isEmpty()) {
      return List.of();
    }

    Map<Long, String> covers = new HashMap<>();
    for (PlaceImage image : images.findForPlaces(byPlace.keySet())) {
      covers.putIfAbsent(image.getPlace().getPlaceId(), image.getImageUrl());
    }
    Map<Long, Object[]> ratings = new HashMap<>();
    reviews.ratingsFor(byPlace.keySet()).forEach(row -> ratings.put((Long) row[0], row));

    return byPlace.values().stream()
        .map(
            listing -> {
              Place place = listing.getPlace();
              Object[] rating = ratings.get(place.getPlaceId());
              return new PartnerPlace(
                  place.getPlaceId(),
                  place.getName(),
                  place.getCategory() == null ? null : place.getCategory().getCode(),
                  place.getCategory() == null ? null : place.getCategory().getName(),
                  place.getAddress(),
                  place.getLatitude(),
                  place.getLongitude(),
                  covers.get(place.getPlaceId()),
                  rating == null
                      ? null
                      : Math.round(((Number) rating[1]).doubleValue() * 10) / 10.0,
                  rating == null ? 0 : ((Number) rating[2]).longValue(),
                  listing.getBusiness().getBusinessName(),
                  true);
            })
        .toList();
  }

  public record PartnerPlace(
      Long placeId,
      String name,
      String categoryCode,
      String categoryName,
      String address,
      Double latitude,
      Double longitude,
      String coverUrl,
      Double rating,
      long reviewCount,
      String businessName,
      boolean verified) {}
}
