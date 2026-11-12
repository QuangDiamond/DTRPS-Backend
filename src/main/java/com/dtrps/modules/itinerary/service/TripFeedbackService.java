package com.dtrps.modules.itinerary.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.TripFeedbackDtos;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.entity.TripFeedback;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.itinerary.repository.TripFeedbackRepository;
import com.dtrps.modules.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đánh giá chuyến đi: khách chấm 1-5 sao và nhận xét. Chỉ khi kế hoạch COMPLETED; mỗi khách đánh
 * giá một kế hoạch đúng một lần (bảng {@code trip_feedback} có unique trên plan_id + user_id).
 */
@Service
public class TripFeedbackService {
  static final String PLAN_NOT_FOUND = "Không tìm thấy kế hoạch du lịch";
  static final String FEEDBACK_NOT_FOUND = "Chưa có đánh giá cho kế hoạch này";
  static final String NOT_COMPLETED = "Chỉ đánh giá được khi chuyến đi đã hoàn thành";
  static final String ALREADY_REVIEWED = "Bạn đã đánh giá chuyến đi này rồi";

  private final TripFeedbackRepository feedbackRepository;
  private final TravelPlanRepository planRepository;
  private final UserRepository userRepository;

  public TripFeedbackService(
      TripFeedbackRepository feedbackRepository,
      TravelPlanRepository planRepository,
      UserRepository userRepository) {
    this.feedbackRepository = feedbackRepository;
    this.planRepository = planRepository;
    this.userRepository = userRepository;
  }

  /** 404 nếu chưa đánh giá — để client hiểu là "chưa đánh giá", không phải lỗi. */
  @Transactional(readOnly = true)
  public TripFeedbackDtos.FeedbackResponse get(Long userId, Long planId) {
    findOwnedPlan(userId, planId);
    return feedbackRepository
        .findByPlanPlanIdAndUserUserId(planId, userId)
        .map(TripFeedbackService::toResponse)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, FEEDBACK_NOT_FOUND));
  }

  @Transactional
  public TripFeedbackDtos.FeedbackResponse create(
      Long userId, Long planId, TripFeedbackDtos.CreateRequest request) {
    TravelPlan plan = findOwnedPlan(userId, planId);

    if (!TravelPlanStatus.COMPLETED.equals(plan.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, NOT_COMPLETED);
    }

    if (feedbackRepository.existsByPlanPlanIdAndUserUserId(planId, userId)) {
      throw new ApiException(HttpStatus.CONFLICT, ALREADY_REVIEWED);
    }

    TripFeedback feedback = new TripFeedback();
    feedback.setPlan(plan);
    feedback.setUser(userRepository.getReferenceById(userId));
    feedback.setRating(request.rating());
    feedback.setComment(blankToNull(request.comment()));

    return toResponse(feedbackRepository.save(feedback));
  }

  private TravelPlan findOwnedPlan(Long userId, Long planId) {
    return planRepository
        .findByPlanIdAndUserUserId(planId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PLAN_NOT_FOUND));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static TripFeedbackDtos.FeedbackResponse toResponse(TripFeedback feedback) {
    return new TripFeedbackDtos.FeedbackResponse(
        feedback.getFeedbackId(),
        feedback.getPlan().getPlanId(),
        feedback.getRating(),
        feedback.getComment(),
        feedback.getCreatedAt());
  }
}
