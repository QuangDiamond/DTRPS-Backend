package com.dtrps.modules.itinerary.service;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job chạy mỗi sáng 08:00 (giờ VN) nhắc các chuyến đi bắt đầu vào ngày mai. Gated bởi
 * {@code app.weather.jobs-enabled=true} giống WeatherNotificationJob để không chạy trùng trên máy
 * dev dùng chung DB.
 */
@Component
@ConditionalOnProperty(name = "app.weather.jobs-enabled", havingValue = "true")
public class TripReminderJob {
  private final TripReminderService reminderService;
  private final Clock clock;

  public TripReminderJob(TripReminderService reminderService, Clock clock) {
    this.reminderService = reminderService;
    this.clock = clock;
  }

  @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Ho_Chi_Minh")
  void remindTomorrowTrips() {
    reminderService.remindUpcomingTrips(LocalDate.now(clock).plusDays(1));
  }
}
