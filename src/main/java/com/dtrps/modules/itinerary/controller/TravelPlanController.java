
package com.dtrps.modules.itinerary.controller;

import com.dtrps.modules.itinerary.dto.TravelPlanDtos;
import com.dtrps.modules.itinerary.service.TravelPlanService;
import com.dtrps.security.CurrentUser;

import jakarta.validation.Valid;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travel-plans")
public class TravelPlanController {

    private final TravelPlanService planService;

    public TravelPlanController(
            TravelPlanService planService
    ) {
        this.planService = planService;
    }

    /**
     * Tạo kế hoạch du lịch mới.
     *
     * POST /api/travel-plans
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TravelPlanDtos.PlanResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody
            TravelPlanDtos.CreateRequest request
    ) {

        Long userId = CurrentUser.id(jwt);

        return planService.create(
                userId,
                request
        );
    }

    /**
     * Xem danh sách kế hoạch của tôi.
     *
     * GET /api/travel-plans
     */
    @GetMapping
    public List<TravelPlanDtos.PlanResponse> getMyPlans(
            @AuthenticationPrincipal Jwt jwt
    ) {

        Long userId = CurrentUser.id(jwt);

        return planService.getMyPlans(userId);
    }

    /**
     * Xem chi tiết một kế hoạch.
     *
     * GET /api/travel-plans/{planId}
     */
    @GetMapping("/{planId}")
    public TravelPlanDtos.PlanResponse getById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId
    ) {

        Long userId = CurrentUser.id(jwt);

        return planService.getById(
                userId,
                planId
        );
    }
}
