package com.dtrps.modules.maps.dto;

/**
 * One address search result. Pass {@code placeId} to /api/maps/places/{placeId} for coordinates.
 */
public record AddressSuggestion(
    String placeId, String title, String subtitle, String description) {}
