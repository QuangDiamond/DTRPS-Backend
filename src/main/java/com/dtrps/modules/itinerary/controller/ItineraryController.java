package com.dtrps.modules.itinerary.controller;

import com.dtrps.modules.itinerary.dto.ItineraryDtos;
import com.dtrps.modules.itinerary.service.ItineraryService;
import com.dtrps.security.CurrentUser;

import jakarta.validation.Valid;

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
@RequestMapping("/api/travel-plans/{planId}/itineraries")
public class ItineraryController {

    private final ItineraryService itineraryService;

    public ItineraryController(
            ItineraryService itineraryService
    ) {
        this.itineraryService = itineraryService;
    }

    /**
     * TASK 45 - Tạo lịch trình theo ngày.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItineraryDtos.ItineraryResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId,
            @Valid @RequestBody ItineraryDtos.CreateRequest request
    ) {

        Long userId = CurrentUser.id(jwt);

        return itineraryService.create(
                userId,
                planId,
                request
        );
    }

    /**
     * Xem lịch trình ACTIVE của kế hoạch.
     */
    @GetMapping("/active")
    public ItineraryDtos.ItineraryResponse getActive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId
    ) {

        Long userId = CurrentUser.id(jwt);

        return itineraryService.getActive(
                userId,
                planId
        );
    }
}