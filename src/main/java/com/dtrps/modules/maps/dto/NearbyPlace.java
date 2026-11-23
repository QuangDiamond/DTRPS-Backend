package com.dtrps.modules.maps.dto;

/** Địa điểm tiện ích lân cận (vd. cây xăng, ATM, quán ăn) tìm quanh vị trí hiện tại qua Goong. */
public record NearbyPlace(
    String placeId,
    String name,
    String address,
    double latitude,
    double longitude,
    long distanceMeters) {}
