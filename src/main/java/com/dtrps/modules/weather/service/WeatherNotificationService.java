package com.dtrps.modules.weather.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.entity.ItineraryDay;
import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.ItineraryItemRepository;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.weather.config.WeatherProperties;
import com.dtrps.modules.weather.dto.WeatherDtos;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cảnh báo trước 1 hôm cho hoạt động NGOÀI TRỜI sắp diễn ra mà rơi vào thời tiết xấu (GBR-48 +
 * chuông). Logic dùng chung cho 2 lối vào:
 *
 * <ul>
 *   <li>{@link WeatherNotificationJob} — quét toàn bộ hệ thống mỗi ngày lúc 18h.
 *   <li>{@link #checkPlanTomorrowIfLate} — gọi ngay khi một kế hoạch vừa chuyển sang PLANNED SAU
 *       18h (xem TravelPlanService.changeStatus), vì nếu đợi tới job 18h hôm sau thì "ngày mai"
 *       lúc đó đã không còn khớp ngày đi thực tế nữa — mất thông báo hẳn, không phải trễ.
 * </ul>
 *
 * Luôn là bean thường (không {@code @ConditionalOnProperty}) để các service khác có thể inject an
 * toàn bất kể cờ {@code app.weather.jobs-enabled} bật hay tắt — tự kiểm tra cờ này bên trong thay
 * vì chặn ở tầng khai báo bean.
 */
@Service
public class WeatherNotificationService {
  private static final Logger log = LoggerFactory.getLogger(WeatherNotificationService.class);
  private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("dd/MM");
  private static final LocalTime LATE_CUTOFF = LocalTime.of(18, 0);

  static final String TYPE_SEVERE = "TRIP_WEATHER_SEVERE";
  static final String TYPE_WARNING = "TRIP_WEATHER_WARNING";

  private final ItineraryItemRepository itemRepository;
  private final WeatherService weatherService;
  private final WeatherProperties properties;
  private final NotificationService notifications;
  private final Clock clock;

  public WeatherNotificationService(
      ItineraryItemRepository itemRepository,
      WeatherService weatherService,
      WeatherProperties properties,
      NotificationService notifications,
      Clock clock) {
    this.itemRepository = itemRepository;
    this.weatherService = weatherService;
    this.properties = properties;
    this.notifications = notifications;
    this.clock = clock;
  }

  /**
   * Gọi ngay sau khi kế hoạch {@code planId} chuyển sang PLANNED. Nếu đã qua 18h hôm nay, báo
   * ngay cho các hoạt động ngoài trời của ngày mai thay vì đợi job 18h hôm sau (sẽ bị lệch ngày,
   * không bao giờ bắt được nữa). Trước 18h thì không làm gì — để job 18h tối nay xử lý bình
   * thường, tránh báo trùng 2 lần cho cùng 1 ngày.
   */
  public void checkPlanTomorrowIfLate(Long planId) {
    if (!properties.jobsEnabledOrDefault() || !LocalTime.now(clock).isAfter(LATE_CUTOFF)) {
      return;
    }
    LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
    for (ItineraryItem candidate : itemRepository.findOutdoorCandidatesForPlan(planId, tomorrow)) {
      checkAndNotify(candidate.getItemId(), tomorrow);
    }
  }

  /** Dùng bởi {@link WeatherNotificationJob} cho đợt quét toàn hệ thống theo lịch. */
  void checkAndNotify(Long itemId, LocalDate targetDate) {
    ItineraryItem item = itemRepository.findById(itemId).orElse(null);
    if (item == null || item.getStartTime() == null || item.getEndTime() == null) {
      return;
    }
    if (targetDate.equals(item.getWeatherNotifiedDate())) {
      return; // đã báo cho đúng ngày này rồi
    }

    ItineraryDay day = item.getDay();
    TravelPlan plan = day.getItinerary().getPlan();
    LocalDate date = plan.getStartDate().plusDays(day.getDayNumber() - 1L);
    if (!date.equals(targetDate)) {
      return; // lịch đã bị dời ngày sau khi lấy danh sách ứng viên
    }

    WeatherDtos.WeatherResponse forecast;
    try {
      forecast = weatherService.forecast(item.getPlace(), date, date);
    } catch (ApiException e) {
      log.warn("Weather lookup failed for item {}: {}", itemId, e.getMessage());
      return;
    }
    if (!forecast.notAvailable().isEmpty()) {
      return;
    }

    List<WeatherDtos.HourlyForecast> windowHours =
        forecast.hourly().stream()
            .filter(
                hour ->
                    !hour.time().isBefore(item.getStartTime())
                        && hour.time().isBefore(item.getEndTime()))
            .toList();

    WeatherDtos.HourlyForecast worstStorm =
        windowHours.stream().filter(this::isStorm).findFirst().orElse(null);
    WeatherDtos.HourlyForecast worstRain =
        windowHours.stream().filter(this::isHeavyRain).findFirst().orElse(null);
    WeatherDtos.HourlyForecast worst = worstStorm != null ? worstStorm : worstRain;
    if (worst == null) {
      return; // không có giờ xấu trong khung giờ hoạt động — không cần báo
    }

    sendNotice(item, day, date, worst, worstStorm != null);
    item.setWeatherNotifiedDate(targetDate);
    itemRepository.save(item);
  }

  private boolean isStorm(WeatherDtos.HourlyForecast hour) {
    return hour.weatherCode() != null
        && properties.stormWeatherCodesOrDefault().contains(hour.weatherCode());
  }

  private boolean isHeavyRain(WeatherDtos.HourlyForecast hour) {
    return hour.precipitationProbability() != null
        && hour.precipitationProbability() >= properties.heavyRainProbabilityOrDefault()
        && hour.precipitation() != null
        && hour.precipitation().doubleValue() >= properties.heavyRainMmPerHourOrDefault();
  }

  private void sendNotice(
      ItineraryItem item,
      ItineraryDay day,
      LocalDate date,
      WeatherDtos.HourlyForecast worst,
      boolean severe) {
    Long userId = day.getItinerary().getPlan().getUser().getUserId();
    String place = item.getPlace().getName();
    String type = severe ? TYPE_SEVERE : TYPE_WARNING;
    String title = severe ? "Cảnh báo giông bão ngày mai" : "Cảnh báo thời tiết xấu ngày mai";
    String message =
        "Ngày mai ("
            + DATE_LABEL.format(date)
            + ") tại \""
            + place
            + "\" dự báo "
            + worst.condition().toLowerCase(java.util.Locale.forLanguageTag("vi"))
            + ", có thể ảnh hưởng hoạt động ngoài trời đã lên lịch.";
    notifications.notifyUser(
        userId,
        new Notice(
            type,
            title,
            message,
            "/trips/itinerary/day/" + day.getDayNumber(),
            Map.of(
                "itemId", item.getItemId(),
                "placeId", item.getPlace().getPlaceId(),
                "date", date.toString())));
  }
}
