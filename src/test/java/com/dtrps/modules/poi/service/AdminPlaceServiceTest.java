package com.dtrps.modules.poi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.dtrps.modules.businessowner.repository.BusinessListingRepository;
import com.dtrps.modules.poi.dto.AdminPlaceStats;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlacePriceRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
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

/** Unit test AdminPlaceService.stats — sheet Poi, UTC042–UTC044 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class AdminPlaceServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");

  /** 01/11/2026 00:00 giờ Việt Nam */
  private static final Instant MONTH_START = Instant.parse("2026-10-31T17:00:00Z");

  @Mock private PlaceRepository places;
  @Mock private PlaceImageRepository images;
  @Mock private PlacePriceRepository prices;
  @Mock private PlaceReviewRepository reviews;
  @Mock private BusinessListingRepository listings;
  @Mock private PlaceDetailsService placeDetails;

  private AdminPlaceService service;

  @BeforeEach
  void setUp() {
    service =
        new AdminPlaceService(
            places,
            images,
            prices,
            reviews,
            listings,
            placeDetails,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("UTC042 Places in several statuses → correct counts per status, source and group")
  void counts() {
    when(places.count()).thenReturn(20L);
    when(listings.count()).thenReturn(8L);
    when(places.countByStatus("ACTIVE")).thenReturn(12L);
    when(places.countByStatus("DRAFT")).thenReturn(5L);
    when(places.countByStatus("PAUSED")).thenReturn(2L);
    when(places.countByStatus("MAINTENANCE")).thenReturn(1L);
    when(listings.countByStatusAndPlaceStatus("PENDING", "DRAFT")).thenReturn(3L);
    when(places.countByCreatedAtGreaterThanEqual(MONTH_START)).thenReturn(4L);
    List<Object[]> groups = new ArrayList<>();
    groups.add(new Object[] {"fnb", 9L});
    groups.add(new Object[] {"lodging", 11L});
    when(places.countByGroup()).thenReturn(groups);

    AdminPlaceStats stats = service.stats();

    assertThat(stats.total()).isEqualTo(20);
    assertThat(stats.active()).isEqualTo(12);
    assertThat(stats.paused()).isEqualTo(3);
    assertThat(stats.partner()).isEqualTo(8);
    assertThat(stats.admin()).isEqualTo(12);
    assertThat(stats.newThisMonth()).isEqualTo(4);
    assertThat(stats.pendingApproval()).isEqualTo(3);
    assertThat(stats.hidden()).isEqualTo(5);
    assertThat(stats.byGroup()).containsEntry("fnb", 9L).containsEntry("lodging", 11L);
  }

  @Test
  @DisplayName("UTC043 Inconsistent data (active + pending > total) → hidden = 0, never negative")
  void inconsistent_hiddenNotNegative() {
    when(places.count()).thenReturn(5L);
    when(places.countByStatus("ACTIVE")).thenReturn(4L);
    when(listings.countByStatusAndPlaceStatus("PENDING", "DRAFT")).thenReturn(3L);

    assertThat(service.stats().hidden()).isZero();
  }

  @Test
  @DisplayName("UTC044 Empty catalog → all counts 0")
  void empty_zero() {
    when(places.countByCreatedAtGreaterThanEqual(any())).thenReturn(0L);

    AdminPlaceStats stats = service.stats();

    assertThat(stats.total()).isZero();
    assertThat(stats.hidden()).isZero();
    assertThat(stats.byGroup()).isEmpty();
  }
}
