package com.dtrps.modules.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.entity.Itinerary;
import com.dtrps.modules.itinerary.entity.ItineraryDay;
import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.ItineraryItemRepository;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.weather.config.WeatherProperties;
import com.dtrps.modules.weather.dto.WeatherDtos;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Cảnh báo trước 1 hôm cho hoạt động ngoài trời gặp thời tiết xấu (TYPE_SEVERE/TYPE_WARNING). */
@ExtendWith(MockitoExtension.class)
class WeatherNotificationServiceTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
  private static final LocalDate TOMORROW = TODAY.plusDays(1);
  private static final Long ITEM_ID = 1L;
  private static final Long PLAN_ID = 7L;
  private static final Long USER_ID = 42L;

  @Mock private ItineraryItemRepository itemRepository;
  @Mock private WeatherService weatherService;
  @Mock private NotificationService notifications;

  private static Clock clockAt(LocalTime time) {
    return Clock.fixed(TODAY.atTime(time).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
  }

  private WeatherNotificationService service(WeatherProperties properties, Clock clock) {
    return new WeatherNotificationService(itemRepository, weatherService, properties, notifications, clock);
  }

  private static WeatherProperties properties(boolean jobsEnabled) {
    return new WeatherProperties(null, 60, 70, 7.6, List.of(65, 67, 82, 95, 96, 99), jobsEnabled);
  }

  private static ItineraryItem buildItem(
      LocalTime start, LocalTime end, boolean outdoor, LocalDate notifiedDate) {
    User user = new User();
    user.setUserId(USER_ID);

    TravelPlan plan = new TravelPlan();
    plan.setPlanId(PLAN_ID);
    plan.setUser(user);
    plan.setStartDate(TOMORROW); // ngày 1 của kế hoạch == ngày mai
    plan.setStatus("PLANNED");

    Itinerary itinerary = new Itinerary();
    itinerary.setPlan(plan);
    itinerary.setStatus("ACTIVE");

    ItineraryDay day = new ItineraryDay();
    day.setItinerary(itinerary);
    day.setDayNumber(1);

    PlaceCategory category = new PlaceCategory();
    category.setIsOutdoor(outdoor);

    Place place = new Place();
    place.setPlaceId(100L);
    place.setName("Chùa Hương");
    place.setCategory(category);

    ItineraryItem item = new ItineraryItem();
    item.setItemId(ITEM_ID);
    item.setDay(day);
    item.setPlace(place);
    item.setStartTime(start);
    item.setEndTime(end);
    item.setWeatherNotifiedDate(notifiedDate);
    return item;
  }

  private static WeatherDtos.HourlyForecast hour(
      LocalTime time, Integer weatherCode, int precipProb, double precipMm) {
    return new WeatherDtos.HourlyForecast(
        TOMORROW,
        time,
        BigDecimal.valueOf(28),
        weatherCode,
        "Giông bão",
        precipProb,
        BigDecimal.valueOf(precipMm),
        BigDecimal.valueOf(10));
  }

  private static WeatherDtos.WeatherResponse response(List<WeatherDtos.HourlyForecast> hourly) {
    return new WeatherDtos.WeatherResponse(100L, List.of(), hourly, List.of(), false, Instant.now());
  }

  // ===== checkAndNotify (dùng bởi job quét 18h) =====

  @Test
  void stormCodeSendsSevereNotificationAndMarksNotified() {
    ItineraryItem item = buildItem(LocalTime.of(9, 0), LocalTime.of(11, 0), true, null);
    WeatherNotificationService service = service(properties(true), clockAt(LocalTime.of(18, 0)));
    when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
    when(weatherService.forecast(item.getPlace(), TOMORROW, TOMORROW))
        .thenReturn(response(List.of(hour(LocalTime.of(10, 0), 95, 80, 10))));

    service.checkAndNotify(ITEM_ID, TOMORROW);

    ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
    verify(notifications).notifyUser(eq(USER_ID), notice.capture());
    assertThat(notice.getValue().type()).isEqualTo(WeatherNotificationService.TYPE_SEVERE);
    assertThat(item.getWeatherNotifiedDate()).isEqualTo(TOMORROW);
    verify(itemRepository).save(item);
  }

  @Test
  void heavyRainWithoutStormSendsWarningNotification() {
    ItineraryItem item = buildItem(LocalTime.of(9, 0), LocalTime.of(11, 0), true, null);
    WeatherNotificationService service = service(properties(true), clockAt(LocalTime.of(18, 0)));
    when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
    when(weatherService.forecast(item.getPlace(), TOMORROW, TOMORROW))
        .thenReturn(response(List.of(hour(LocalTime.of(10, 0), 61, 90, 10))));

    service.checkAndNotify(ITEM_ID, TOMORROW);

    ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
    verify(notifications).notifyUser(eq(USER_ID), notice.capture());
    assertThat(notice.getValue().type()).isEqualTo(WeatherNotificationService.TYPE_WARNING);
  }

  @Test
  void clearWeatherSendsNoNotification() {
    ItineraryItem item = buildItem(LocalTime.of(9, 0), LocalTime.of(11, 0), true, null);
    WeatherNotificationService service = service(properties(true), clockAt(LocalTime.of(18, 0)));
    when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
    when(weatherService.forecast(item.getPlace(), TOMORROW, TOMORROW))
        .thenReturn(response(List.of(hour(LocalTime.of(10, 0), 1, 10, 0.0))));

    service.checkAndNotify(ITEM_ID, TOMORROW);

    verify(notifications, never()).notifyUser(any(), any());
    assertThat(item.getWeatherNotifiedDate()).isNull();
  }

  @Test
  void alreadyNotifiedForTargetDateIsSkipped() {
    ItineraryItem item = buildItem(LocalTime.of(9, 0), LocalTime.of(11, 0), true, TOMORROW);
    WeatherNotificationService service = service(properties(true), clockAt(LocalTime.of(18, 0)));
    when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

    service.checkAndNotify(ITEM_ID, TOMORROW);

    verify(notifications, never()).notifyUser(any(), any());
    verify(weatherService, never()).forecast(any(Place.class), any(), any());
  }

  @Test
  void itemRescheduledAwayFromTargetDateIsSkipped() {
    ItineraryItem item = buildItem(LocalTime.of(9, 0), LocalTime.of(11, 0), true, null);
    item.getDay().getItinerary().getPlan().setStartDate(TOMORROW.plusDays(1));
    WeatherNotificationService service = service(properties(true), clockAt(LocalTime.of(18, 0)));
    when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

    service.checkAndNotify(ITEM_ID, TOMORROW);

    verify(notifications, never()).notifyUser(any(), any());
    verify(weatherService, never()).forecast(any(Place.class), any(), any());
  }

  @Test
  void gatewayFailureIsCaughtAndSkipped() {
    ItineraryItem item = buildItem(LocalTime.of(9, 0), LocalTime.of(11, 0), true, null);
    WeatherNotificationService service = service(properties(true), clockAt(LocalTime.of(18, 0)));
    when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
    when(weatherService.forecast(item.getPlace(), TOMORROW, TOMORROW))
        .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "unavailable"));

    service.checkAndNotify(ITEM_ID, TOMORROW);

    verify(notifications, never()).notifyUser(any(), any());
  }

  // ===== checkPlanTomorrowIfLate (báo ngay khi kế hoạch PLANNED sau 18h) =====

  @Test
  void jobsDisabledNeverChecksEvenAfter18h() {
    WeatherNotificationService service = service(properties(false), clockAt(LocalTime.of(20, 0)));

    service.checkPlanTomorrowIfLate(PLAN_ID);

    verify(itemRepository, never()).findOutdoorCandidatesForPlan(any(), any());
  }

  @Test
  void beforeCutoffLetsTheScheduledJobHandleItInstead() {
    WeatherNotificationService service = service(properties(true), clockAt(LocalTime.of(17, 59)));

    service.checkPlanTomorrowIfLate(PLAN_ID);

    verify(itemRepository, never()).findOutdoorCandidatesForPlan(any(), any());
  }

  @Test
  void afterCutoffChecksAndNotifiesImmediately() {
    ItineraryItem item = buildItem(LocalTime.of(9, 0), LocalTime.of(11, 0), true, null);
    WeatherNotificationService service = service(properties(true), clockAt(LocalTime.of(20, 0)));
    when(itemRepository.findOutdoorCandidatesForPlan(PLAN_ID, TOMORROW)).thenReturn(List.of(item));
    when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
    when(weatherService.forecast(item.getPlace(), TOMORROW, TOMORROW))
        .thenReturn(response(List.of(hour(LocalTime.of(10, 0), 95, 80, 10))));

    service.checkPlanTomorrowIfLate(PLAN_ID);

    verify(notifications).notifyUser(eq(USER_ID), any(Notice.class));
    assertThat(item.getWeatherNotifiedDate()).isEqualTo(TOMORROW);
  }
}
