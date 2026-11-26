package com.dtrps.modules.weather.service;

import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.repository.ItineraryItemRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Quét toàn hệ thống mỗi ngày lúc 18h cho các hoạt động ngoài trời của NGÀY MAI — logic đánh giá
 * thời tiết + gửi thông báo nằm ở {@link WeatherNotificationService} (dùng chung với lối vào
 * "báo ngay" khi kế hoạch tạo sau 18h). Gated bởi {@code app.weather.jobs-enabled}, giống
 * WeatherRefreshJob/AdLifecycleJobs — dùng chung DB giữa các máy dev nên job phải tắt mặc định.
 */
@Component
@ConditionalOnProperty(name = "app.weather.jobs-enabled", havingValue = "true")
public class WeatherNotificationJob {
  private final ItineraryItemRepository itemRepository;
  private final WeatherNotificationService notificationService;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public WeatherNotificationJob(
      ItineraryItemRepository itemRepository,
      WeatherNotificationService notificationService,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.itemRepository = itemRepository;
    this.notificationService = notificationService;
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Ho_Chi_Minh")
  void notifyTomorrow() {
    LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
    for (ItineraryItem candidate :
        itemRepository.findOutdoorCandidatesForWeatherNotification(tomorrow)) {
      Long itemId = candidate.getItemId();
      transaction.executeWithoutResult(status -> notificationService.checkAndNotify(itemId, tomorrow));
    }
  }
}
