package com.dtrps.modules.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TripNavigationArrivalTest {

  @Test
  @DisplayName("without accuracy the arrival radius is 50 m")
  void defaultRadius() {
    assertThat(TripNavigationService.isArrived(49, null)).isTrue();
    assertThat(TripNavigationService.isArrived(51, null)).isFalse();
  }

  @Test
  @DisplayName("the radius follows GPS accuracy but stays between 50 and 100 m")
  void radiusFollowsAccuracy() {
    assertThat(TripNavigationService.isArrived(80, 80.0)).isTrue();
    assertThat(TripNavigationService.isArrived(90, 20.0)).isFalse();
    assertThat(TripNavigationService.isArrived(100, 5_000.0)).isTrue();
    assertThat(TripNavigationService.isArrived(101, 5_000.0)).isFalse();
  }
}
