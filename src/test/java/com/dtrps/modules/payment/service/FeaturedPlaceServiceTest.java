package com.dtrps.modules.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.modules.advertisement.entity.Advertisement;
import com.dtrps.modules.advertisement.repository.AdvertisementRepository;
import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.payment.dto.AdDtos;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test FeaturedPlaceService — sheet Payment, UTC071–UTC073 trong Report5.1 Unit Test. Điều
 * kiện lọc (gói hết hạn, địa điểm tạm ngưng, đối tác bị khoá) nằm trong câu JPQL {@code
 * findFeatured}; ở đây chỉ kiểm tra service hỏi đúng thời điểm và không tự thêm địa điểm nào ngoài
 * kết quả đó.
 */
@ExtendWith(MockitoExtension.class)
class FeaturedPlaceServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");

  @Mock private AdvertisementRepository advertisements;
  @Mock private PlaceImageRepository images;
  @Mock private PlaceReviewRepository reviews;

  private FeaturedPlaceService service;

  @BeforeEach
  void setUp() {
    service =
        new FeaturedPlaceService(advertisements, images, reviews, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static Advertisement ad(long id) {
    Place place = new Place();
    place.setPlaceId(100 + id);
    place.setName("Địa điểm " + id);
    BusinessListing listing = new BusinessListing();
    listing.setPlace(place);
    Advertisement ad = new Advertisement();
    ad.setAdvertisementId(id);
    ad.setListing(listing);
    return ad;
  }

  @Test
  @DisplayName("UTC071 Có 3 quảng cáo đang chạy: trả 3 địa điểm kèm ảnh bìa và điểm đánh giá")
  void threeAds_mapped() {
    when(advertisements.findFeatured(NOW)).thenReturn(List.of(ad(1), ad(2), ad(3)));
    PlaceImage cover = new PlaceImage();
    cover.setPlace(ad(1).getListing().getPlace());
    cover.setImageUrl("https://res.cloudinary.com/x/1.jpg");
    when(images.findForPlaces(any())).thenReturn(List.of(cover));
    List<Object[]> ratings = new ArrayList<>();
    ratings.add(new Object[] {101L, 4.26, 7L});
    when(reviews.ratingsFor(any())).thenReturn(ratings);

    List<AdDtos.FeaturedPlace> result = service.featured(8);

    assertThat(result)
        .extracting(AdDtos.FeaturedPlace::id)
        .containsExactlyInAnyOrder(101L, 102L, 103L);
    AdDtos.FeaturedPlace first =
        result.stream().filter(item -> item.id() == 101L).findFirst().orElseThrow();
    assertThat(first.coverUrl()).isEqualTo("https://res.cloudinary.com/x/1.jpg");
    assertThat(first.rating()).isEqualTo(4.3);
    assertThat(first.reviewCount()).isEqualTo(7);
    AdDtos.FeaturedPlace other =
        result.stream().filter(item -> item.id() == 102L).findFirst().orElseThrow();
    assertThat(other.rating()).isNull();
    assertThat(other.reviewCount()).isZero();
  }

  @Test
  @DisplayName("UTC072 Quảng cáo hết hạn: lọc theo thời điểm hiện tại, không có thì trả rỗng")
  void noLiveAds_empty() {
    when(advertisements.findFeatured(NOW)).thenReturn(List.of());

    assertThat(service.featured(8)).isEmpty();
    verify(images, never()).findForPlaces(any());
  }

  @Test
  @DisplayName("UTC073 Chỉ trả địa điểm trong kết quả lọc, tối đa limit, cùng ngày thì cùng thứ tự")
  void limitAndStableOrder() {
    List<Advertisement> live = new ArrayList<>();
    for (long id = 1; id <= 10; id++) {
      live.add(ad(id));
    }
    when(advertisements.findFeatured(NOW)).thenReturn(live);

    List<Long> first = service.featured(8).stream().map(AdDtos.FeaturedPlace::id).toList();
    List<Long> again = service.featured(8).stream().map(AdDtos.FeaturedPlace::id).toList();

    assertThat(first).hasSize(8).doesNotHaveDuplicates().allMatch(id -> id > 100 && id <= 110);
    assertThat(again).isEqualTo(first);
  }

  @Test
  @DisplayName("UTC074 limit=0: trả danh sách rỗng, không tải ảnh và đánh giá")
  void zeroLimit_empty() {
    when(advertisements.findFeatured(NOW)).thenReturn(List.of(ad(1), ad(2), ad(3)));

    assertThat(service.featured(0)).isEmpty();
    verify(images, never()).findForPlaces(any());
    verify(reviews, never()).ratingsFor(any());
  }
}
