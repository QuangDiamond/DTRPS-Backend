
package com.dtrps.modules.discovery.controller;

import com.dtrps.modules.discovery.service.RecommendationService;
import com.dtrps.modules.discovery.service.RecommendationService.RecommendationResponse;
import com.dtrps.security.CurrentUser;

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

    public RecommendationController(
            RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
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
}
