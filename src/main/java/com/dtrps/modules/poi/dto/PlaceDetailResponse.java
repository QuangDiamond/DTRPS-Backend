package com.dtrps.modules.poi.dto;

import java.util.List;

/** Full place for travellers: the list row plus content, hours, prices and photos. */
public record PlaceDetailResponse(
    PlaceSummaryResponse summary,
    String description,
    Double latitude,
    Double longitude,
    String phone,
    String website,
    List<OpeningHourDto> openingHours,
    List<PriceDto> prices,
    List<ImageDto> images) {}
