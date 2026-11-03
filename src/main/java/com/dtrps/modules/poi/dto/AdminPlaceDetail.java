package com.dtrps.modules.poi.dto;

import java.util.List;

/** Full place for the admin: the catalogue row plus content, hours, prices and photos. */
public record AdminPlaceDetail(
    AdminPlaceSummary summary,
    String description,
    Double latitude,
    Double longitude,
    String phone,
    String website,
    List<OpeningHourDto> openingHours,
    List<PriceDto> prices,
    List<ImageDto> images) {}
