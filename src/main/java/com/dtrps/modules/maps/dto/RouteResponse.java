package com.dtrps.modules.maps.dto;

import java.util.List;

/**
 * Best route between two points. {@code polyline} is the encoded overview line (Google polyline
 * format) the client decodes to draw the route. {@code steps} are the turn-by-turn manoeuvres in
 * driving order (empty when Goong sends none). {@code alternatives} holds optional alternative
 * routes returned by Goong when {@code alternatives=true}.
 */
public record RouteResponse(
    long distanceMeters,
    long durationSeconds,
    String distanceText,
    String durationText,
    String polyline,
    List<RouteStep> steps,
    List<RouteResponse> alternatives) {

  /** Giữ tương thích với code/test cũ chỉ truyền 6 tham số. */
  public RouteResponse(
      long distanceMeters,
      long durationSeconds,
      String distanceText,
      String durationText,
      String polyline,
      List<RouteStep> steps) {
    this(distanceMeters, durationSeconds, distanceText, durationText, polyline, steps, List.of());
  }
}
