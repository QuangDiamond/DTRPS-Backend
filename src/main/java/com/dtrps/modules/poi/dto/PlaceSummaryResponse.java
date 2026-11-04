package com.dtrps.modules.poi.dto;

/** One row of the public place list. {@code rating} is null without reviews. */
public record PlaceSummaryResponse(
    Long id,
    String name,
    ReferenceItem group,
    ReferenceItem category,
    ReferenceItem province,
    String address,
    String coverUrl,
    Double rating,
    long reviewCount) {}
