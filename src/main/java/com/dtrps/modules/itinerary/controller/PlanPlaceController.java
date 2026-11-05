package com.dtrps.modules.itinerary.controller;

import com.dtrps.modules.itinerary.dto.PlanPlaceDtos;
import com.dtrps.modules.itinerary.service.PlanPlaceService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travel-plans/{planId}")
public class PlanPlaceController {
    private final PlanPlaceService service;
    public PlanPlaceController(PlanPlaceService service) { this.service = service; }

    /** Lọc địa điểm của catalogue; AI Embedding sẽ có endpoint riêng do thành viên AI triển khai. */
    @GetMapping("/candidate-places")
    public List<PlanPlaceDtos.PlaceResponse> candidates(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
        return service.candidates(CurrentUser.id(jwt), planId);
    }

    @GetMapping("/places")
    public PlanPlaceDtos.SelectedResponse list(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
        return service.list(CurrentUser.id(jwt), planId);
    }

    @PostMapping("/places")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanPlaceDtos.PlaceResponse add(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable Long planId, @Valid @RequestBody PlanPlaceDtos.AddRequest request) {
        return service.add(CurrentUser.id(jwt), planId, request.placeId());
    }

    @DeleteMapping("/places/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId,
                       @PathVariable Long placeId) {
        service.remove(CurrentUser.id(jwt), planId, placeId);
    }
}