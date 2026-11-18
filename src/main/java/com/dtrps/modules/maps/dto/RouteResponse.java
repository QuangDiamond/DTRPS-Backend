package com.dtrps.modules.maps.dto;

import java.util.List;

/**
 * Best route between two points. {@code polyline} is the encoded overview line (Google polyline
 * format) the client decodes to draw the route. {@code steps} are the turn-by-turn manoeuvres in
 * driving order (empty when Goong sends none).
 */
public record RouteResponse(
    long distanceMeters,
    long durationSeconds,
    String distanceText,
    String durationText,
    String polyline,
    List<RouteStep> steps) {}
