package com.dtrps.modules.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.weather.config.WeatherProperties;
import com.dtrps.modules.weather.dto.WeatherDtos;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class WeatherNotificationServiceTest {

  private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
  private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 27);

  private static Clock clockAt(int hour, int minute) {
    Instant instant = TODAY.atTime(hour, minute).atZone(VN).toInstant();
    return Clock.fixed(instant, VN);
  }

  private static WeatherProperties props(boolean jobsEnabled) {
    return new WeatherProperties(
        "https://api.open-meteo.com", 60, 70, 5.0, List.of(95, 96, 99), jobsEnabled);
  }

  private static ItineraryItem item(Long itemId, Long planId, int dayNumber, LocalDate startDate) {
    User user = new User();
    user.setUserId(42L);

    TravelPlan plan = new TravelPlan();
    plan.setPlanId(planId);
    plan.setUser(user);
    plan.setStartDate(startDate);

    Itinerary itinerary = new Itinerary();
    itinerary.setPlan(plan);

    ItineraryDay day = new ItineraryDay();
    day.setDayNumber(dayNumber);
    day.setItinerary(itinerary);

    Place place = new Place();
    place.setPlaceId(100L);
    place.setName("Bà Nà Hills");

    ItineraryItem item = new ItineraryItem();
    item.setItemId(itemId);
    item.setDay(day);
    item.setPlace(place);
    item.setStartTime(LocalTime.of(9, 0));
    item.setEndTime(LocalTime.of(12, 0));
    return item;
  }

  private static WeatherDtos.WeatherResponse weatherWith(
      List<WeatherDtos.HourlyForecast> hourly, List<LocalDate> notAvailable) {
    return new WeatherDtos.WeatherResponse(
        100L, List.of(), hourly, notAvailable, false, Instant.now());
  }

  private static WeatherDtos.HourlyForecast hour(
      LocalTime time, Integer code, Integer rainProb, Double rainMm, String condition) {
    return new WeatherDtos.HourlyForecast(
        TOMORROW,
        time,
        BigDecimal.valueOf(25.0),
        code,
        condition,
        rainProb,
        rainMm == null ? null : BigDecimal.valueOf(rainMm),
        BigDecimal.valueOf(10.0));
  }

  // ===== checkPlanTomorrowIfLate =====

  @Test
  void checkPlanTomorrowIfLate_jobsDisabled_doesNothing() {
    var repo = mock(ItineraryItemRepository.class);
    var weather = mock(WeatherService.class);
    var notices = mock(NotificationService.class);
    var service =
        new WeatherNotificationService(repo, weather, props(false), notices, clockAt(19, 0));

    service.checkPlanTomorrowIfLate(10L);

    verify(repo, never()).findOutdoorCandidatesForPlan(any(), any());
  }

  @Test
  void checkPlanTomorrowIfLate_atOrBefore18hCutoff_doesNothing() {
    var repo = mock(ItineraryItemRepository.class);
    var weather = mock(WeatherService.class);
    var notices = mock(NotificationService.class);
    var service =
        new WeatherNotificationService(repo, weather, props(true), notices, clockAt(18, 0));

    service.checkPlanTomorrowIfLate(10L);

    verify(repo, never()).findOutdoorCandidatesForPlan(any(), any());
  }

  @Test
  void checkPlanTomorrowIfLate_after18hAndJobsEnabled_checksOutdoorCandidates() {
    var repo = mock(ItineraryItemRepository.class);
    var weather = mock(WeatherService.class);
    var notices = mock(NotificationService.class);
    var service =
        new WeatherNotificationService(repo, weather, props(true), notices, clockAt(18, 5));

    ItineraryItem cand = item(1L, 10L, 1, TOMORROW);
    when(repo.findOutdoorCandidatesForPlan(10L, TOMORROW)).thenReturn(List.of(cand));
    when(repo.findById(1L)).thenReturn(Optional.of(cand));
    when(weather.forecast(cand.getPlace(), TOMORROW, TOMORROW))
        .thenReturn(
            weatherWith(
                List.of(hour(LocalTime.of(10, 0), 95, 90, 12.0, "Giông bão")), List.of()));

    service.checkPlanTomorrowIfLate(10L);

    verify(notices).notifyUser(eq(42L), any(Notice.class));
    verify(repo).save(cand);
  }

  // ===== checkAndNotify =====

  @Test
  void checkAndNotify_stormInWindow_sendsSevereNoticeWithPlanLink() {
    var repo = mock(ItineraryItemRepository.class);
    var weather = mock(WeatherService.class);
    var notices = mock(NotificationService.class);
    var service =
        new WeatherNotificationService(repo, weather, props(true), notices, clockAt(18, 0));

    ItineraryItem cand = item(5L, 10L, 2, TODAY);
    when(repo.findById(5L)).thenReturn(Optional.of(cand));
    when(weather.forecast(cand.getPlace(), TOMORROW, TOMORROW))
        .thenReturn(
            weatherWith(
                List.of(hour(LocalTime.of(9, 0), 95, 85, 8.0, "Giông bão")), List.of()));

    service.checkAndNotify(5L, TOMORROW);

    ArgumentCaptor<Notice> cap = ArgumentCaptor.forClass(Notice.class);
    verify(notices).notifyUser(eq(42L), cap.capture());
    Notice sent = cap.getValue();
    assertThat(sent.type()).isEqualTo(WeatherNotificationService.TYPE_SEVERE);
    assertThat(sent.title()).isEqualTo("Cảnh báo giông bão ngày mai");
    assertThat(sent.link()).isEqualTo("/trips/itinerary/day/2?planId=10");
    assertThat(sent.message()).contains("27/11").contains("Bà Nà Hills").contains("giông bão");
    assertThat(cand.getWeatherNotifiedDate()).isEqualTo(TOMORROW);
    verify(repo).save(cand);
  }

  @Test
  void checkAndNotify_heavyRainWithoutStorm_sendsWarningNotice() {
    var repo = mock(ItineraryItemRepository.class);
    var weather = mock(WeatherService.class);
    var notices = mock(NotificationService.class);
    var service =
        new WeatherNotificationService(repo, weather, props(true), notices, clockAt(18, 0));

    ItineraryItem cand = item(6L, 10L, 1, TOMORROW);
    when(repo.findById(6L)).thenReturn(Optional.of(cand));
    when(weather.forecast(cand.getPlace(), TOMORROW, TOMORROW))
        .thenReturn(
            weatherWith(
                List.of(hour(LocalTime.of(11, 0), 63, 80, 6.5, "Mưa vừa")), List.of()));

    service.checkAndNotify(6L, TOMORROW);

    ArgumentCaptor<Notice> cap = ArgumentCaptor.forClass(Notice.class);
    verify(notices).notifyUser(eq(42L), cap.capture());
    assertThat(cap.getValue().type()).isEqualTo(WeatherNotificationService.TYPE_WARNING);
    assertThat(cap.getValue().title()).isEqualTo("Cảnh báo thời tiết xấu ngày mai");
    assertThat(cand.getWeatherNotifiedDate()).isEqualTo(TOMORROW);
    verify(repo).save(cand);
  }

  @Test
  void checkAndNotify_badWeatherOutsideActivityWindow_doesNotNotify() {
    var repo = mock(ItineraryItemRepository.class);
    var weather = mock(WeatherService.class);
    var notices = mock(NotificationService.class);
    var service =
        new WeatherNotificationService(repo, weather, props(true), notices, clockAt(18, 0));

    ItineraryItem cand = item(7L, 10L, 1, TOMORROW); // 09:00..12:00
    when(repo.findById(7L)).thenReturn(Optional.of(cand));
    when(weather.forecast(cand.getPlace(), TOMORROW, TOMORROW))
        .thenReturn(
            weatherWith(
                List.of(
                    hour(LocalTime.of(10, 0), 0, 10, 0.0, "Trời quang"),
                    hour(LocalTime.of(12, 0), 95, 95, 15.0, "Giông bão")),
                List.of()));

    service.checkAndNotify(7L, TOMORROW);

    verify(notices, never()).notifyUser(any(), any());
    verify(repo, never()).save(any());
  }

  @Test
  void checkAndNotify_alreadyNotifiedForDate_skipsDuplicate() {
    var repo = mock(ItineraryItemRepository.class);
    var weather = mock(WeatherService.class);
    var notices = mock(NotificationService.class);
    var service =
        new WeatherNotificationService(repo, weather, props(true), notices, clockAt(18, 0));

    ItineraryItem cand = item(8L, 10L, 1, TOMORROW);
    cand.setWeatherNotifiedDate(TOMORROW);
    when(repo.findById(8L)).thenReturn(Optional.of(cand));

    service.checkAndNotify(8L, TOMORROW);

    verify(weather, never()).forecast(any(Place.class), any(), any());
    verify(notices, never()).notifyUser(any(), any());
  }

  @Test
  void checkAndNotify_missingTimesOrShiftedDateOrApiError_skipsSafely() {
    var repo = mock(ItineraryItemRepository.class);
    var weather = mock(WeatherService.class);
    var notices = mock(NotificationService.class);
    var service =
        new WeatherNotificationService(repo, weather, props(true), notices, clockAt(18, 0));

    // Missing startTime
    ItineraryItem noTime = item(9L, 10L, 1, TOMORROW);
    noTime.setStartTime(null);
    when(repo.findById(9L)).thenReturn(Optional.of(noTime));
    service.checkAndNotify(9L, TOMORROW);

    // Date shifted away from targetDate
    ItineraryItem shifted = item(10L, 10L, 1, TOMORROW.plusDays(2));
    when(repo.findById(10L)).thenReturn(Optional.of(shifted));
    service.checkAndNotify(10L, TOMORROW);

    // Weather API error
    ItineraryItem apiErr = item(11L, 10L, 1, TOMORROW);
    when(repo.findById(11L)).thenReturn(Optional.of(apiErr));
    when(weather.forecast(apiErr.getPlace(), TOMORROW, TOMORROW))
        .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "Open-Meteo unavailable"));
    service.checkAndNotify(11L, TOMORROW);

    // Forecast not available
    ItineraryItem unavail = item(12L, 10L, 1, TOMORROW);
    when(repo.findById(12L)).thenReturn(Optional.of(unavail));
    when(weather.forecast(unavail.getPlace(), TOMORROW, TOMORROW))
        .thenReturn(weatherWith(List.of(), List.of(TOMORROW)));
    service.checkAndNotify(12L, TOMORROW);

    verify(notices, never()).notifyUser(any(), any());
    verify(repo, never()).save(any());
  }
}
