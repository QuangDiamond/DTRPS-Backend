package com.dtrps.modules.maps.service;

/** Straight-line distance between two points on the Earth (haversine). */
public final class GeoDistance {
  private static final double EARTH_RADIUS_METERS = 6_371_000.0;

  private GeoDistance() {}

  public static double meters(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double h =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2)
                * Math.sin(dLng / 2);
    return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
  }
}
