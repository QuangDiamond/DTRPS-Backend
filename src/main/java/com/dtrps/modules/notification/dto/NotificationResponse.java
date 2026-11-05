package com.dtrps.modules.notification.dto;

import java.time.Instant;

/** One saved notification. {@code link} is the page to open when it is clicked. */
public record NotificationResponse(
    Long id,
    String type,
    String title,
    String message,
    String link,
    boolean read,
    Instant createdAt) {}
