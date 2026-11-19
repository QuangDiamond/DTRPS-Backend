package com.dtrps.modules.notification.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationCategoryTest {

  @Test
  void groupsExistingTypesByPrefix() {
    assertThat(NotificationCategory.of("PARTNER_SUBMITTED")).isEqualTo(NotificationCategory.LISTING);
    assertThat(NotificationCategory.of("LISTING_APPROVED")).isEqualTo(NotificationCategory.LISTING);
    assertThat(NotificationCategory.of("REVISION_SUBMITTED"))
        .isEqualTo(NotificationCategory.LISTING);
    assertThat(NotificationCategory.of("AD_PAYMENT_ISSUE_CREATED"))
        .isEqualTo(NotificationCategory.ADS);
    assertThat(NotificationCategory.of("AD_SUBSCRIPTION_EXPIRING"))
        .isEqualTo(NotificationCategory.ADS);
    assertThat(NotificationCategory.of("PLACE_REVIEW_REPORTED"))
        .isEqualTo(NotificationCategory.REVIEW);
    assertThat(NotificationCategory.of("REVIEW_HIDDEN")).isEqualTo(NotificationCategory.REVIEW);
    assertThat(NotificationCategory.of("REVIEW_REPORT_RESOLVED"))
        .isEqualTo(NotificationCategory.REVIEW);
  }

  @Test
  void tripFeedbackIsAReviewButOtherTripTypesAreTrips() {
    assertThat(NotificationCategory.of("TRIP_FEEDBACK_CREATED"))
        .isEqualTo(NotificationCategory.REVIEW);
    assertThat(NotificationCategory.of("TRIP_CREATED")).isEqualTo(NotificationCategory.TRIP);
  }

  @Test
  void unknownTypesFallBackToOther() {
    assertThat(NotificationCategory.of("SYSTEM_BROADCAST")).isEqualTo(NotificationCategory.OTHER);
    assertThat(NotificationCategory.of(null)).isEqualTo(NotificationCategory.OTHER);
  }

  @Test
  void parsesTabNamesCaseInsensitively() {
    assertThat(NotificationCategory.parse("ads")).contains(NotificationCategory.ADS);
    assertThat(NotificationCategory.parse("nope")).isEmpty();
  }
}
