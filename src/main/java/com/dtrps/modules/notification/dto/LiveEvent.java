package com.dtrps.modules.notification.dto;

import java.util.Map;

/**
 * Pushed to signed-in browsers over the event stream. {@code type} says what changed (e.g.
 * LISTING_APPROVED) so pages can refresh just what they show; {@code data} carries the ids involved
 * (businessId, listingId, revisionId, placeId). {@code notificationId} is set when the event was
 * also saved as a notification (null for a silent refresh).
 */
public record LiveEvent(
    String type,
    Long notificationId,
    String title,
    String message,
    String link,
    Map<String, Object> data) {}
