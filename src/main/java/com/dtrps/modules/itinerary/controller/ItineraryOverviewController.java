
package com.dtrps.modules.itinerary.controller;

import com.dtrps.modules.itinerary.dto.ItineraryOverviewDtos;
import com.dtrps.modules.itinerary.service.ItineraryOverviewService;
import com.dtrps.security.CurrentUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travel-plans/{planId}/itineraries")
public class ItineraryOverviewController {

    private final ItineraryOverviewService overviewService;

    public ItineraryOverviewController(
            ItineraryOverviewService overviewService
    ) {
        this.overviewService = overviewService;
    }

    /**
     * TASK 47
     * Xem lịch trình ACTIVE và tổng chi phí.
     */
    @GetMapping("/active/overview")
    public ItineraryOverviewDtos.OverviewResponse getActiveOverview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId
    ) {

        Long userId = CurrentUser.id(jwt);

        return overviewService.getActiveOverview(
                userId,
                planId
        );
    }

    /**
     * TASK 47
     * Xem lịch trình cụ thể theo itineraryId.
     */
    @GetMapping("/{itineraryId}/overview")
    public ItineraryOverviewDtos.OverviewResponse getOverviewById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId,
            @PathVariable Long itineraryId
    ) {

        Long userId = CurrentUser.id(jwt);

        return overviewService.getOverviewById(
                userId,
                planId,
                itineraryId
        );
    }
}
