package com.dtrps.modules.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TripReminderServiceTest {

  private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
  private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 27);

  private static TravelPlan plan(Long planId, Long userId, String destination, String title) {
    User user = new User();
    user.setUserId(userId);
    TravelPlan p = new TravelPlan();
    p.setPlanId(planId);
    p.setUser(user);
    p.setDestination(destination);
    p.setTitle(title);
    p.setStatus("PLANNED");
    p.setStartDate(TOMORROW);
    return p;
  }

  @Test
  void remindUpcomingTrips_sendsNoticeAndSkipsDuplicate() {
    var planRepo = mock(TravelPlanRepository.class);
    var notifRepo = mock(NotificationRepository.class);
    var notices = mock(NotificationService.class);
    var service = new TripReminderService(planRepo, notifRepo, notices);

    TravelPlan p1 = plan(10L, 1L, "Đà Nẵng", "Chuyến Đà Nẵng");
    TravelPlan p2 = plan(11L, 2L, null, "Khám phá Hội An");
    when(planRepo.findByStatusAndStartDate("PLANNED", TOMORROW)).thenReturn(List.of(p1, p2));
    when(notifRepo.existsByUserUserIdAndTypeAndLink(
            1L, TripReminderService.TYPE_UPCOMING, "/trips/itinerary?planId=10"))
        .thenReturn(false);
    when(notifRepo.existsByUserUserIdAndTypeAndLink(
            2L, TripReminderService.TYPE_UPCOMING, "/trips/itinerary?planId=11"))
        .thenReturn(true);

    int sent = service.remindUpcomingTrips(TOMORROW);

    assertThat(sent).isEqualTo(1);
    ArgumentCaptor<Notice> cap = ArgumentCaptor.forClass(Notice.class);
    verify(notices).notifyUser(eq(1L), cap.capture());
    assertThat(cap.getValue().type()).isEqualTo(TripReminderService.TYPE_UPCOMING);
    assertThat(cap.getValue().link()).isEqualTo("/trips/itinerary?planId=10");
    assertThat(cap.getValue().message()).contains("27/11/2026").contains("Đà Nẵng");
    verify(notices, never()).notifyUser(eq(2L), any());
  }

  @Test
  void remindTripReview_completedPlan_sendsNoticeOnce() {
    var planRepo = mock(TravelPlanRepository.class);
    var notifRepo = mock(NotificationRepository.class);
    var notices = mock(NotificationService.class);
    var service = new TripReminderService(planRepo, notifRepo, notices);

    TravelPlan p = plan(20L, 5L, null, "Hành trình Huế");
    when(notifRepo.existsByUserUserIdAndTypeAndLink(
            5L, TripReminderService.TYPE_REVIEW, "/trips/itinerary?planId=20"))
        .thenReturn(false)
        .thenReturn(true);

    service.remindTripReview(p);
    service.remindTripReview(p); // second call should skip

    ArgumentCaptor<Notice> cap = ArgumentCaptor.forClass(Notice.class);
    verify(notices).notifyUser(eq(5L), cap.capture());
    assertThat(cap.getValue().type()).isEqualTo(TripReminderService.TYPE_REVIEW);
    assertThat(cap.getValue().message()).contains("Hành trình Huế");
  }

  @Test
  void remindTripReview_nullOrIncompletePlan_doesNothing() {
    var notices = mock(NotificationService.class);
    var service =
        new TripReminderService(
            mock(TravelPlanRepository.class), mock(NotificationRepository.class), notices);

    service.remindTripReview(null);
    service.remindTripReview(new TravelPlan());

    verify(notices, never()).notifyUser(any(), any());
  }

  @Test
  void tripReminderJob_delegatesTomorrowDateToService() {
    var reminderService = mock(TripReminderService.class);
    Clock clock = Clock.fixed(TODAY.atTime(8, 0).atZone(VN).toInstant(), VN);
    var job = new TripReminderJob(reminderService, clock);

    job.remindTomorrowTrips();

    verify(reminderService).remindUpcomingTrips(TOMORROW);
  }
}
