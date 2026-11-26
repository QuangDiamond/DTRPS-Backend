package com.dtrps.modules.weather.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.repository.ItineraryItemRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Job chỉ còn vai trò lập lịch (18h mỗi ngày) + lấy danh sách ứng viên rồi ủy quyền đánh giá/gửi
 * cho {@link WeatherNotificationService} — logic nghiệp vụ test chi tiết ở
 * {@code WeatherNotificationServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class WeatherNotificationJobTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
  private static final LocalDate TOMORROW = TODAY.plusDays(1);
  private static final Clock CLOCK =
      Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
  private static final Long ITEM_ID = 1L;

  @Mock private ItineraryItemRepository itemRepository;
  @Mock private WeatherNotificationService notificationService;
  @Mock private PlatformTransactionManager transactionManager;

  private WeatherNotificationJob job;

  @BeforeEach
  void setUp() {
    job = new WeatherNotificationJob(itemRepository, notificationService, transactionManager, CLOCK);
  }

  @Test
  void scansTomorrowAndDelegatesEachCandidateToService() {
    ItineraryItem candidate = new ItineraryItem();
    candidate.setItemId(ITEM_ID);
    when(itemRepository.findOutdoorCandidatesForWeatherNotification(TOMORROW))
        .thenReturn(List.of(candidate));

    job.notifyTomorrow();

    verify(notificationService).checkAndNotify(eq(ITEM_ID), eq(TOMORROW));
  }
}
