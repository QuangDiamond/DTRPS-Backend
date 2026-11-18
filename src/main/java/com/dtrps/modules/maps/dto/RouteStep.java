package com.dtrps.modules.maps.dto;

/**
 * One manoeuvre of a route (turn-by-turn). {@code instruction} is plain text; {@code maneuver} is
 * Goong's code (for example {@code turn-right}) and may be null. The start point is where the
 * manoeuvre happens, so the client can tell the traveller "in 200 m, turn right".
 */
public record RouteStep(
    String instruction,
    String maneuver,
    long distanceMeters,
    long durationSeconds,
    String distanceText,
    double startLatitude,
    double startLongitude,
    double endLatitude,
    double endLongitude) {}
