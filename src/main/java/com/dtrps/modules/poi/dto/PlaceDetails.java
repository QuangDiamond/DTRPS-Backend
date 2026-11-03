package com.dtrps.modules.poi.dto;

import java.util.List;

/** Hours, prices and photos of a place. */
public record PlaceDetails(
    List<OpeningHourDto> openingHours, List<PriceDto> prices, List<ImageDto> images) {
  public static PlaceDetails empty() {
    return new PlaceDetails(List.of(), List.of(), List.of());
  }
}
