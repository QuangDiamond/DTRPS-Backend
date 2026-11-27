package com.dtrps.modules.weather.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.repository.ItineraryItemRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class WeatherNotificationJobTest {

  private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
  private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 27);

  @Test
  void notifyTomorrow_processesEveryOutdoorCandidateInTransaction() {
    var repo = mock(ItineraryItemRepository.class);
    var service = mock(WeatherNotificationService.class);
    var txManager = mock(PlatformTransactionManager.class);
    when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

    ItineraryItem i1 = new ItineraryItem();
    i1.setItemId(101L);
    ItineraryItem i2 = new ItineraryItem();
    i2.setItemId(102L);
    when(repo.findOutdoorCandidatesForWeatherNotification(TOMORROW)).thenReturn(List.of(i1, i2));

    Clock clock = Clock.fixed(TODAY.atTime(18, 0).atZone(VN).toInstant(), VN);
    var job = new WeatherNotificationJob(repo, service, txManager, clock);

    job.notifyTomorrow();

    verify(service).checkAndNotify(101L, TOMORROW);
    verify(service).checkAndNotify(102L, TOMORROW);
  }

  @Test
  void notifyTomorrow_noCandidates_doesNothing() {
    var repo = mock(ItineraryItemRepository.class);
    var service = mock(WeatherNotificationService.class);
    var txManager = mock(PlatformTransactionManager.class);
    when(repo.findOutdoorCandidatesForWeatherNotification(TOMORROW)).thenReturn(List.of());

    Clock clock = Clock.fixed(TODAY.atTime(18, 0).atZone(VN).toInstant(), VN);
    var job = new WeatherNotificationJob(repo, service, txManager, clock);

    job.notifyTomorrow();

    verify(service, never()).checkAndNotify(any(), any());
  }
}
