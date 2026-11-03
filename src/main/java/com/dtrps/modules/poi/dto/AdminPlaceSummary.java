package com.dtrps.modules.poi.dto;

import java.time.Instant;

/**
 * One row of the admin place catalogue. {@code source} is PARTNER when a business listing owns the
 * place, otherwise ADMIN (a POI created by the platform). {@code rating} is null without reviews.
 */
public record AdminPlaceSummary(
    Long id,
    String name,
    ReferenceItem group,
    ReferenceItem category,
    ReferenceItem province,
    String address,
    String status,
    String source,
    Long listingId,
    String listingStatus,
    String businessName,
    String coverUrl,
    int imageCount,
    Double rating,
    long reviewCount,
    Instant createdAt,
    Instant updatedAt) {}
