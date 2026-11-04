package com.dtrps.modules.maps.dto;

/** A point with its address, from a search result or from coordinates (reverse geocoding). */
public record GeoLocation(
    String placeId, String name, String address, double latitude, double longitude) {}
