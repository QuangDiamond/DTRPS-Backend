
package com.dtrps.modules.itinerary.dto;

import com.dtrps.modules.maps.dto.RouteResponse;

/**
 * Dữ liệu chỉ đường tới địa điểm tiếp theo
 * của ngày đang thực hiện.
 *
 * Backend không tự đọc GPS của thiết bị.
 * Frontend gửi tọa độ hiện tại khi gọi API
 * điều hướng, và Backend dùng Goong Directions
 * để tính tuyến.
 *
 * Tọa độ hiện tại không được lưu vào database
 * bởi DTO này.
 */
public final class TripNavigationDtos {

    private TripNavigationDtos() {}

    /**
     * Điểm đến là hoạt động PENDING đầu tiên
     * theo itemOrder trong ngày.
     *
     * RouteResponse chứa:
     * - distanceMeters
     * - durationSeconds
     * - distanceText
     * - durationText
     * - polyline
     */
    public record NextRouteResponse(
            Long planId,
            Long itineraryId,
            Long dayId,
            Integer dayNumber,
            Long itemId,
            Integer itemOrder,
            Long placeId,
            String placeName,
            String placeAddress,
            Double destinationLatitude,
            Double destinationLongitude,
            String vehicle,
            RouteResponse route
    ) {}
}
