package com.dtrps.modules.poi.dto;

import java.util.List;

/** One row of the public place list. {@code rating} is null without reviews. */
public record PlaceSummaryResponse(
    Long id,
    String name,
    ReferenceItem group,
    ReferenceItem category,
    /** Every group / type of the place, the main one first */
    List<ReferenceItem> groups,
    List<PlaceTypes.Type> categories,
    ReferenceItem province,
    String address,
    String coverUrl,
    Double rating,
    long reviewCount) {}
