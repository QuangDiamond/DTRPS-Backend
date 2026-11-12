package com.dtrps.modules.itinerary.controller;

import com.dtrps.modules.itinerary.dto.TripFeedbackDtos;
import com.dtrps.modules.itinerary.service.TripFeedbackService;
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

/**
 * Đánh giá chuyến đi của chủ kế hoạch: 1-5 sao + nhận xét. Chỉ khi kế hoạch COMPLETED, một lần cho
 * mỗi kế hoạch.
 */
@RestController
@RequestMapping("/api/travel-plans/{planId}/feedback")
public class TripFeedbackController {
  private final TripFeedbackService feedbackService;

  public TripFeedbackController(TripFeedbackService feedbackService) {
    this.feedbackService = feedbackService;
  }

  /** 404 nếu chưa đánh giá kế hoạch này. */
  @GetMapping
  public TripFeedbackDtos.FeedbackResponse get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
    return feedbackService.get(CurrentUser.id(jwt), planId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TripFeedbackDtos.FeedbackResponse create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long planId,
      @Valid @RequestBody TripFeedbackDtos.CreateRequest request) {
    return feedbackService.create(CurrentUser.id(jwt), planId, request);
  }
}
