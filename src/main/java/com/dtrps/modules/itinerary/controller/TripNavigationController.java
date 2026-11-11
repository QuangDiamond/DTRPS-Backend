
package com.dtrps.modules.itinerary.controller;

import com.dtrps.modules.itinerary.dto.TripNavigationDtos;
import com.dtrps.modules.itinerary.service.TripNavigationService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API chỉ đường từ GPS của Traveller tới địa điểm
 * chưa hoàn thành tiếp theo trong ngày đang diễn ra.
 *
 * Trình duyệt lấy GPS sau khi người dùng cấp quyền.
 * Controller chỉ nhận tọa độ và gọi TripNavigationService,
 * không lưu lịch sử vị trí.
 *
 * Chức năng bắt đầu ngày, hoàn thành địa điểm và hoàn thành
 * ngày thuộc TripProgressController.
 */
@RestController
@Validated
@RequestMapping("/api/travel-plans/{planId}/navigation")
public class TripNavigationController {

    private final TripNavigationService tripNavigationService;

    public TripNavigationController(
            TripNavigationService tripNavigationService
    ) {
        this.tripNavigationService = tripNavigationService;
    }

    /**
     * GET /api/travel-plans/{planId}/navigation/days/{dayNumber}/next-route
     *
     * Ví dụ query:
     * ?fromLat=21.0285&fromLng=105.8542&vehicle=car
     *
     * Service thực hiện:
     * 1. Kiểm tra Traveller sở hữu TravelPlan.
     * 2. Kiểm tra ngày đang IN_PROGRESS.
     * 3. Tìm địa điểm PENDING đầu tiên.
     * 4. Gọi MapService.directions() để lấy tuyến Goong.
     * 5. Trả địa điểm tiếp theo và RouteResponse.
     *
     * Khi Traveller hoàn thành/bỏ qua địa điểm hiện tại,
     * Frontend gọi lại API để lấy tuyến đến địa điểm kế tiếp.
     */
    @GetMapping("/days/{dayNumber}/next-route")
    public TripNavigationDtos.NextRouteResponse getNextRoute(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId,
            @PathVariable
            @Min(
                    value = 1,
                    message = "Số ngày phải lớn hơn 0"
            )
            Integer dayNumber,
            @RequestParam
            @DecimalMin("-90")
            @DecimalMax("90")
            double fromLat,
            @RequestParam
            @DecimalMin("-180")
            @DecimalMax("180")
            double fromLng,
            @RequestParam(defaultValue = "car")
            @Pattern(
                    regexp = "^(car|bike|taxi|truck|hd)$",
                    message = "Phương tiện phải là car, bike, taxi, truck hoặc hd"
            )
            String vehicle
    ) {
        return tripNavigationService.getNextRoute(
                CurrentUser.id(jwt),
                planId,
                dayNumber,
                fromLat,
                fromLng,
                vehicle
        );
    }
}
