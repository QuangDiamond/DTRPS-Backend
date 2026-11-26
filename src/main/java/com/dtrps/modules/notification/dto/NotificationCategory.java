package com.dtrps.modules.notification.dto;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Tab of the notification bell. The only place that says which notification type belongs where: a
 * new type joins a tab by its prefix, and a type no tab claims lands in OTHER, so it is never lost.
 * Order matters — the first tab that matches wins (TRIP_FEEDBACK_* is a review, not a trip).
 */
public enum NotificationCategory {
  REVIEW(List.of("TRIP_FEEDBACK_"), List.of("REVIEW")),
  LISTING(List.of("PARTNER_", "LISTING_", "REVISION_"), List.of()),
  ADS(List.of("AD_"), List.of()),
  TRIP(List.of("TRIP_"), List.of()),
  WEATHER(List.of("WEATHER_"), List.of()),
  OTHER(List.of(), List.of());

  private final List<String> prefixes;
  private final List<String> keywords;

  NotificationCategory(List<String> prefixes, List<String> keywords) {
    this.prefixes = prefixes;
    this.keywords = keywords;
  }

  public static NotificationCategory of(String type) {
    String value = type == null ? "" : type;
    for (NotificationCategory category : values()) {
      if (category.prefixes.stream().anyMatch(value::startsWith)
          || category.keywords.stream().anyMatch(value::contains)) {
        return category;
      }
    }
    return OTHER;
  }

  /** Case-insensitive; empty for an unknown name. */
  public static Optional<NotificationCategory> parse(String name) {
    try {
      return Optional.of(valueOf(name.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException | NullPointerException e) {
      return Optional.empty();
    }
  }
}
