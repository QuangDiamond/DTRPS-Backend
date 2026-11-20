package com.dtrps.modules.discovery.controller;

import com.dtrps.modules.discovery.service.PartnerPlaceService;
import com.dtrps.modules.discovery.service.PartnerPlaceService.PartnerPlace;
import com.dtrps.modules.discovery.service.RecommendationService;
import com.dtrps.modules.discovery.service.RecommendationService.RecommendationResponse;
import com.dtrps.security.CurrentUser;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travel-plans/{planId}")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final PartnerPlaceService partnerPlaceService;

    public RecommendationController(
            RecommendationService recommendationService,
            PartnerPlaceService partnerPlaceService) {
        this.recommendationService = recommendationService;
        this.partnerPlaceService = partnerPlaceService;
    }

    @GetMapping("/recommended-places")
    public RecommendationResponse recommend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId) {

        Long userId = CurrentUser.id(jwt);

        return recommendationService.recommend(
            userId,
            planId
        );
    }

    /** Địa điểm của đối tác đã xác minh tại điểm đến của kế hoạch (cột riêng ở màn Gợi ý). */
    @GetMapping("/partner-places")
    public List<PartnerPlace> partnerPlaces(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId) {

        return partnerPlaceService.forPlan(CurrentUser.id(jwt), planId);
    }
}
