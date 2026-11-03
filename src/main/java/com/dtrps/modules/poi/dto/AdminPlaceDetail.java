package com.dtrps.modules.poi.dto;

import java.util.List;

/**
 * Full place for the admin: the catalogue row plus content, hours, prices and photos. {@code
 * editable} is false for partner places, which change only through partner revisions.
 */
public record AdminPlaceDetail(
    AdminPlaceSummary summary,
    String description,
    Double latitude,
    Double longitude,
    String phone,
    String website,
    boolean editable,
    List<OpeningHourDto> openingHours,
    List<AdminPlacePrice> prices,
    List<AdminPlaceImage> images) {}
