
package com.dtrps.modules.itinerary.controller;

import com.dtrps.modules.itinerary.dto.TripProgressDtos;
import com.dtrps.modules.itinerary.service.TripProgressService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý tiến độ thực hiện lịch trình theo từng ngày.
 *
 * Chỉ chủ sở hữu TravelPlan được xem hoặc cập nhật tiến độ;
 * TripProgressService chịu trách nhiệm kiểm tra quyền và trạng thái.
 *
 * GPS và chỉ đường Goong sử dụng /api/maps/directions,
 * không xử lý trong Controller này.
 */
@RestController
@RequestMapping("/api/travel-plans/{planId}/trip-progress")
public class TripProgressController {

    private final TripProgressService tripProgressService;

    public TripProgressController(TripProgressService tripProgressService) {
        this.tripProgressService = tripProgressService;
    }

    /**
     * GET /api/travel-plans/{planId}/trip-progress
     *
     * Xem tiến độ toàn bộ chuyến đi.
     */
    @GetMapping
    public TripProgressDtos.ProgressResponse getProgress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId) {

        return tripProgressService.getProgress(
                CurrentUser.id(jwt),
                planId
        );
    }

    /**
     * POST /api/travel-plans/{planId}/trip-progress/days/{dayNumber}/start
     *
     * Bắt đầu Ngày 1, Ngày 2...
     */
    @PostMapping("/days/{dayNumber}/start")
    public TripProgressDtos.ProgressResponse startDay(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId,
            @PathVariable Integer dayNumber) {

        return tripProgressService.startDay(
                CurrentUser.id(jwt),
                planId,
                dayNumber
        );
    }

    /**
     * PATCH /api/travel-plans/{planId}/trip-progress/days/{dayNumber}/activities/{itemId}
     *
     * Đánh dấu hoạt động COMPLETED hoặc SKIPPED.
     */
    @PatchMapping("/days/{dayNumber}/activities/{itemId}")
    public TripProgressDtos.ProgressResponse updateActivity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId,
            @PathVariable Integer dayNumber,
            @PathVariable Long itemId,
            @Valid @RequestBody TripProgressDtos.UpdateActivityRequest request) {

        return tripProgressService.updateActivity(
                CurrentUser.id(jwt),
                planId,
                dayNumber,
                itemId,
                request
        );
    }

    /**
     * POST /api/travel-plans/{planId}/trip-progress/days/{dayNumber}/complete
     *
     * Hoàn thành một ngày khi tất cả hoạt động đã được xử lý.
     */
    @PostMapping("/days/{dayNumber}/complete")
    public TripProgressDtos.ProgressResponse completeDay(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId,
            @PathVariable Integer dayNumber) {

        return tripProgressService.completeDay(
                CurrentUser.id(jwt),
                planId,
                dayNumber
        );
    }
}
