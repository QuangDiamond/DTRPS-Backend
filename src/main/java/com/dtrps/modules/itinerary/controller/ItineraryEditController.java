package com.dtrps.modules.itinerary.controller;

import com.dtrps.modules.itinerary.service.ItineraryEditService;
import com.dtrps.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/travel-plans/{planId}/itineraries/active")
public class ItineraryEditController {
    private final ItineraryEditService service;

    public ItineraryEditController(ItineraryEditService service) {
        this.service = service;
    }

    @PutMapping("/edit-activity")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void edit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId,
                     @RequestBody ItineraryEditService.EditRequest request) {
        service.edit(CurrentUser.id(jwt), planId, request);
    }
}
