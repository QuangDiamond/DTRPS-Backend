package com.dtrps.modules.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.TripFeedbackDtos;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.entity.TripFeedback;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.itinerary.repository.TripFeedbackRepository;
import com.dtrps.modules.poi.service.AdminReviewService;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TripFeedbackServiceTest {

  private final TripFeedbackRepository feedbackRepo = mock(TripFeedbackRepository.class);
  private final TravelPlanRepository planRepo = mock(TravelPlanRepository.class);
  private final UserRepository userRepo = mock(UserRepository.class);
  private final AdminReviewService adminReviews = mock(AdminReviewService.class);
  private final TripFeedbackService service =
      new TripFeedbackService(feedbackRepo, planRepo, userRepo, adminReviews);

  private static TravelPlan plan(Long id, String status) {
    TravelPlan p = new TravelPlan();
    p.setPlanId(id);
    p.setStatus(status);
    return p;
  }

  @Test
  void get_existingFeedback_returnsResponse() {
    TravelPlan p = plan(10L, TravelPlanStatus.COMPLETED);
    TripFeedback fb = new TripFeedback();
    fb.setFeedbackId(1L);
    fb.setPlan(p);
    fb.setRating((short) 5);
    fb.setComment("Rất tuyệt");
    when(planRepo.findByPlanIdAndUserUserId(10L, 2L)).thenReturn(Optional.of(p));
    when(feedbackRepo.findByPlanPlanIdAndUserUserId(10L, 2L)).thenReturn(Optional.of(fb));

    var res = service.get(2L, 10L);
    assertThat(res.rating()).isEqualTo((short) 5);
    assertThat(res.comment()).isEqualTo("Rất tuyệt");
  }

  @Test
  void get_notReviewedYet_throwsNotFound() {
    when(planRepo.findByPlanIdAndUserUserId(10L, 2L))
        .thenReturn(Optional.of(plan(10L, TravelPlanStatus.COMPLETED)));
    when(feedbackRepo.findByPlanPlanIdAndUserUserId(10L, 2L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(2L, 10L))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining(TripFeedbackService.FEEDBACK_NOT_FOUND);
  }

  @Test
  void create_completedPlan_savesAndNotifiesAdmin() {
    TravelPlan p = plan(10L, TravelPlanStatus.COMPLETED);
    User u = new User();
    u.setUserId(2L);
    when(planRepo.findByPlanIdAndUserUserId(10L, 2L)).thenReturn(Optional.of(p));
    when(feedbackRepo.existsByPlanPlanIdAndUserUserId(10L, 2L)).thenReturn(false);
    when(userRepo.getReferenceById(2L)).thenReturn(u);
    when(feedbackRepo.save(any(TripFeedback.class)))
        .thenAnswer(
            inv -> {
              TripFeedback saved = inv.getArgument(0);
              saved.setFeedbackId(99L);
              return saved;
            });

    var res = service.create(2L, 10L, new TripFeedbackDtos.CreateRequest((short) 5, "  Tuyệt vời  "));
    assertThat(res.feedbackId()).isEqualTo(99L);
    assertThat(res.comment()).isEqualTo("Tuyệt vời");
    verify(adminReviews).tripFeedbackCreated(any(TripFeedback.class));
  }

  @Test
  void create_planNotCompleted_throwsConflict() {
    when(planRepo.findByPlanIdAndUserUserId(10L, 2L))
        .thenReturn(Optional.of(plan(10L, TravelPlanStatus.IN_PROGRESS)));

    assertThatThrownBy(() -> service.create(2L, 10L, new TripFeedbackDtos.CreateRequest((short) 4, "Ok")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining(TripFeedbackService.NOT_COMPLETED);
  }

  @Test
  void create_alreadyReviewed_throwsConflict() {
    when(planRepo.findByPlanIdAndUserUserId(10L, 2L))
        .thenReturn(Optional.of(plan(10L, TravelPlanStatus.COMPLETED)));
    when(feedbackRepo.existsByPlanPlanIdAndUserUserId(10L, 2L)).thenReturn(true);

    assertThatThrownBy(() -> service.create(2L, 10L, new TripFeedbackDtos.CreateRequest((short) 4, "Ok")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining(TripFeedbackService.ALREADY_REVIEWED);
  }
}
