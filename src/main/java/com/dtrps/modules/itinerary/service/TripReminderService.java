package com.dtrps.modules.itinerary.service;

import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.user.repository.NotificationRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gửi thông báo nhắc lịch khởi hành (trước 1 ngày) và nhắc đánh giá sau khi hoàn thành chuyến đi.
 * Chặn gửi trùng bằng {@link NotificationRepository#existsByUserUserIdAndTypeAndLink} nên không cần
 * thêm cột vào DB chung.
 */
@Service
public class TripReminderService {
  public static final String TYPE_UPCOMING = "TRIP_UPCOMING_REMINDER";
  public static final String TYPE_REVIEW = "TRIP_REVIEW_REMINDER";
  private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final TravelPlanRepository planRepository;
  private final NotificationRepository notificationRepository;
  private final NotificationService notifications;

  public TripReminderService(
      TravelPlanRepository planRepository,
      NotificationRepository notificationRepository,
      NotificationService notifications) {
    this.planRepository = planRepository;
    this.notificationRepository = notificationRepository;
    this.notifications = notifications;
  }

  /** Nhắc tất cả chuyến đi trạng thái PLANNED bắt đầu vào {@code tomorrow}. */
  @Transactional
  public int remindUpcomingTrips(LocalDate tomorrow) {
    int sent = 0;
    for (TravelPlan plan : planRepository.findByStatusAndStartDate("PLANNED", tomorrow)) {
      Long userId = plan.getUser().getUserId();
      String link = "/trips/itinerary?planId=" + plan.getPlanId();
      if (notificationRepository.existsByUserUserIdAndTypeAndLink(userId, TYPE_UPCOMING, link)) {
        continue;
      }
      String title = "Chuyến đi sắp khởi hành ngày mai";
      String destination = plan.getDestination() != null ? plan.getDestination() : plan.getTitle();
      String message =
          "Ngày mai ("
              + DATE_LABEL.format(tomorrow)
              + ") bạn có chuyến đi \""
              + destination
              + "\". Nhớ kiểm tra lịch trình và chuẩn bị hành lý nhé!";
      notifications.notifyUser(
          userId,
          new Notice(TYPE_UPCOMING, title, message, link, Map.of("planId", plan.getPlanId())));
      sent++;
    }
    return sent;
  }

  /** Gửi lời mời đánh giá ngay khi du khách hoàn thành ngày cuối của chuyến đi. */
  @Transactional
  public void remindTripReview(TravelPlan plan) {
    if (plan == null || plan.getUser() == null || plan.getPlanId() == null) {
      return;
    }
    Long userId = plan.getUser().getUserId();
    String link = "/trips/itinerary?planId=" + plan.getPlanId();
    if (notificationRepository.existsByUserUserIdAndTypeAndLink(userId, TYPE_REVIEW, link)) {
      return;
    }
    String destination = plan.getDestination() != null ? plan.getDestination() : plan.getTitle();
    String title = "Chuyến đi đã hoàn thành!";
    String message =
        "Chúc mừng bạn đã hoàn thành chuyến đi \""
            + destination
            + "\". Hãy chia sẻ đánh giá để giúp SmartTravel gợi ý tốt hơn nhé!";
    notifications.notifyUser(
        userId,
        new Notice(TYPE_REVIEW, title, message, link, Map.of("planId", plan.getPlanId())));
  }
}
