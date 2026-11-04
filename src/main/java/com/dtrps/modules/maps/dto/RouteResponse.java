package com.dtrps.modules.maps.dto;

/**
 * Best route between two points. {@code polyline} is the encoded overview line (Google polyline
 * format) the client decodes to draw the route.
 */
public record RouteResponse(
    long distanceMeters,
    long durationSeconds,
    String distanceText,
    String durationText,
    String polyline) {}
