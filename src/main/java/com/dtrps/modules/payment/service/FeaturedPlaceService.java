package com.dtrps.modules.payment.service;

import com.dtrps.modules.advertisement.entity.Advertisement;
import com.dtrps.modules.advertisement.repository.AdvertisementRepository;
import com.dtrps.modules.payment.dto.AdDtos;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Địa điểm đang được quảng cáo ở trang chủ, xoay vòng theo ngày để các đối tác được hiện đều. */
@Service
public class FeaturedPlaceService {
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

  private final AdvertisementRepository advertisements;
  private final PlaceImageRepository images;
  private final PlaceReviewRepository reviews;
  private final Clock clock;

  public FeaturedPlaceService(
      AdvertisementRepository advertisements,
      PlaceImageRepository images,
      PlaceReviewRepository reviews,
      Clock clock) {
    this.advertisements = advertisements;
    this.images = images;
    this.reviews = reviews;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<AdDtos.FeaturedPlace> featured(int limit) {
    Instant now = clock.instant();
    long day = LocalDate.ofInstant(now, VIETNAM).toEpochDay();
    List<Place> places =
        advertisements.findFeatured(now).stream()
            .sorted(
                Comparator.comparingInt(
                    (Advertisement ad) -> Objects.hash(ad.getAdvertisementId(), day)))
            .limit(limit)
            .map(ad -> ad.getListing().getPlace())
            .toList();
    if (places.isEmpty()) {
      return List.of();
    }
    List<Long> ids = places.stream().map(Place::getPlaceId).toList();
    Map<Long, String> covers = new HashMap<>();
    for (PlaceImage image : images.findForPlaces(ids)) {
      covers.putIfAbsent(image.getPlace().getPlaceId(), image.getImageUrl());
    }
    Map<Long, Object[]> ratings = new HashMap<>();
    reviews.ratingsFor(ids).forEach(row -> ratings.put((Long) row[0], row));
    return places.stream()
        .map(
            place -> {
              Object[] rating = ratings.get(place.getPlaceId());
              return new AdDtos.FeaturedPlace(
                  place.getPlaceId(),
                  place.getName(),
                  place.getCategory() == null ? null : place.getCategory().getName(),
                  place.getProvince() == null ? null : place.getProvince().getName(),
                  covers.get(place.getPlaceId()),
                  rating == null
                      ? null
                      : Math.round(((Number) rating[1]).doubleValue() * 10) / 10.0,
                  rating == null ? 0 : ((Number) rating[2]).longValue());
            })
        .toList();
  }
}
