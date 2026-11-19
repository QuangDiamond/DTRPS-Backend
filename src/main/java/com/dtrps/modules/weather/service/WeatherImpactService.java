package com.dtrps.modules.weather.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.ItineraryItemDtos;
import com.dtrps.modules.itinerary.entity.Itinerary;
import com.dtrps.modules.itinerary.entity.ItineraryDay;
import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.ItineraryDayRepository;
import com.dtrps.modules.itinerary.repository.ItineraryItemRepository;
import com.dtrps.modules.itinerary.repository.ItineraryRepository;
import com.dtrps.modules.itinerary.service.ItineraryItemService;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.weather.config.WeatherProperties;
import com.dtrps.modules.weather.dto.WeatherDtos;
import com.dtrps.modules.weather.dto.WeatherImpactDtos;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-045 Weather Impact (GBR-48): flags outdoor-category activities whose place + time window
 * hits the configured heavy-rain/storm thresholds, and proposes — never auto-applies — an
 * adjustment. {@link #apply} only runs when the traveller picks one, and goes back through
 * {@link ItineraryItemService}'s own create/update/delete so every existing check there (plan
 * ownership, itinerary must be ACTIVE, plan must be editable, trip not already started, start/end
 * time ordering, place must be ACTIVE) still applies — this service does not re-implement any of
 * that. Note: at the time of writing, this codebase has no cross-item overlap check, no opening-
 * hours check and no per-item budget check anywhere (including in ItineraryItemService itself),
 * so none of those are enforced here either — only what already exists is reused.
 */
@Service
public class WeatherImpactService {
  // MSG40: cảnh báo hoạt động ngoài trời có thể bị ảnh hưởng bởi thời tiết xấu
  static final String WARNING_MESSAGE = "Hoạt động ngoài trời này có thể bị ảnh hưởng bởi thời tiết xấu";
  static final String ITINERARY_NOT_FOUND = "Không tìm thấy lịch trình";
  static final String ITEM_NOT_FOUND = "Không tìm thấy hoạt động";
  static final String ITEM_NOT_IN_ITINERARY = "Hoạt động không thuộc lịch trình này";
  static final String UNKNOWN_SUGGESTION_TYPE = "Loại đề xuất không hợp lệ";
  static final String MISSING_SUGGESTION_FIELDS = "Thiếu thông tin để áp dụng đề xuất";
  private static final LocalTime DAY_WINDOW_START = LocalTime.of(6, 0);
  private static final LocalTime DAY_WINDOW_END = LocalTime.of(21, 0);
  private static final int NEARBY_INDOOR_LIMIT = 3;

  private final ItineraryRepository itineraryRepository;
  private final ItineraryDayRepository dayRepository;
  private final ItineraryItemRepository itemRepository;
  private final ItineraryItemService itemService;
  private final PlaceRepository placeRepository;
  private final WeatherService weatherService;
  private final WeatherProperties properties;

  public WeatherImpactService(
      ItineraryRepository itineraryRepository,
      ItineraryDayRepository dayRepository,
      ItineraryItemRepository itemRepository,
      ItineraryItemService itemService,
      PlaceRepository placeRepository,
      WeatherService weatherService,
      WeatherProperties properties) {
    this.itineraryRepository = itineraryRepository;
    this.dayRepository = dayRepository;
    this.itemRepository = itemRepository;
    this.itemService = itemService;
    this.placeRepository = placeRepository;
    this.weatherService = weatherService;
    this.properties = properties;
  }

  // Không readOnly: weatherService.forecast() có thể cần ghi lại cache khi đã hết hạn 1 giờ
  // (GBR-49), bị gọi lồng trong transaction này nên phải cùng cho phép ghi.
  @Transactional
  public WeatherDtos.ItineraryWeatherResponse weatherForItinerary(Long userId, Long itineraryId) {
    Itinerary itinerary = requireOwnedItinerary(userId, itineraryId);
    TravelPlan plan = itinerary.getPlan();

    List<ItineraryDay> days =
        dayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itineraryId);

    Set<Place> places = new LinkedHashSet<>();
    for (ItineraryDay day : days) {
      for (ItineraryItem item : itemRepository.findOrderedByDayId(day.getDayId())) {
        places.add(item.getPlace());
      }
    }

    List<WeatherDtos.WeatherResponse> responses =
        places.stream()
            .map(place -> weatherService.forecast(place, plan.getStartDate(), plan.getEndDate()))
            .toList();

    return new WeatherDtos.ItineraryWeatherResponse(itineraryId, responses);
  }

  // Không readOnly: evaluateItem() gọi weatherService.forecast(), có thể cần ghi lại cache khi
  // đã hết hạn 1 giờ (GBR-49), bị gọi lồng trong transaction này nên phải cùng cho phép ghi.
  @Transactional
  public WeatherImpactDtos.ImpactResponse evaluate(Long userId, Long itineraryId) {
    Itinerary itinerary = requireOwnedItinerary(userId, itineraryId);
    TravelPlan plan = itinerary.getPlan();

    List<ItineraryDay> days =
        dayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itineraryId);

    List<WeatherImpactDtos.ImpactedItem> impacted = new ArrayList<>();
    for (ItineraryDay day : days) {
      LocalDate date = plan.getStartDate().plusDays(day.getDayNumber() - 1L);
      for (ItineraryItem item : itemRepository.findOrderedByDayId(day.getDayId())) {
        evaluateItem(item, day, date, days, plan).ifPresent(impacted::add);
      }
    }
    return new WeatherImpactDtos.ImpactResponse(itineraryId, impacted);
  }

  @Transactional
  public ItineraryItemDtos.ItemResponse apply(
      Long userId, Long itineraryId, WeatherImpactDtos.ApplyRequest request) {
    Itinerary itinerary = requireOwnedItinerary(userId, itineraryId);

    ItineraryItem item =
        itemRepository
            .findById(request.itemId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ITEM_NOT_FOUND));
    if (!item.getDay().getItinerary().getItineraryId().equals(itineraryId)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, ITEM_NOT_IN_ITINERARY);
    }

    WeatherImpactDtos.SuggestionType type = parseSuggestionType(request.suggestionType());
    Long dayId = item.getDay().getDayId();

    return switch (type) {
      case SHIFT_TIME -> {
        requireTimes(request);
        yield itemService.update(userId, dayId, item.getItemId(), updateRequestFor(item, request.newStartTime(), request.newEndTime(), item.getPlace().getPlaceId()));
      }
      case SWAP_PLACE -> {
        if (request.newPlaceId() == null) {
          throw new ApiException(HttpStatus.BAD_REQUEST, MISSING_SUGGESTION_FIELDS);
        }
        yield itemService.update(userId, dayId, item.getItemId(), updateRequestFor(item, item.getStartTime(), item.getEndTime(), request.newPlaceId()));
      }
      case CHANGE_DAY -> {
        if (request.newDate() == null) {
          throw new ApiException(HttpStatus.BAD_REQUEST, MISSING_SUGGESTION_FIELDS);
        }
        LocalTime start = request.newStartTime() != null ? request.newStartTime() : item.getStartTime();
        LocalTime end = request.newStartTime() != null ? request.newEndTime() : item.getEndTime();
        TravelPlan plan = itinerary.getPlan();
        int dayNumber = (int) (request.newDate().toEpochDay() - plan.getStartDate().toEpochDay()) + 1;
        ItineraryDay targetDay =
            dayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itineraryId).stream()
                .filter(d -> d.getDayNumber().equals(dayNumber))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Ngày đề xuất không thuộc lịch trình"));

        ItineraryItemDtos.CreateRequest createRequest =
            new ItineraryItemDtos.CreateRequest(
                item.getPlace().getPlaceId(),
                start,
                end,
                item.getEstimatedCost(),
                item.getTransportMode() == null ? null : item.getTransportMode().getTransportModeId(),
                item.getTravelTimeMinutes(),
                item.getNotes());

        itemService.delete(userId, dayId, item.getItemId());
        yield itemService.create(userId, targetDay.getDayId(), createRequest);
      }
    };
  }

  // ===== Helpers =====

  private java.util.Optional<WeatherImpactDtos.ImpactedItem> evaluateItem(
      ItineraryItem item, ItineraryDay day, LocalDate date, List<ItineraryDay> allDays, TravelPlan plan) {
    PlaceCategory category = item.getPlace().getCategory();
    boolean outdoor = category != null && Boolean.TRUE.equals(category.getIsOutdoor());
    if (!outdoor || item.getStartTime() == null || item.getEndTime() == null) {
      return java.util.Optional.empty();
    }

    WeatherDtos.WeatherResponse forecast = weatherService.forecast(item.getPlace(), date, date);
    if (!forecast.notAvailable().isEmpty()) {
      return java.util.Optional.empty(); // GBR-47: ngoài 7 ngày tới, không đánh giá được
    }

    List<WeatherDtos.HourlyForecast> badHours =
        forecast.hourly().stream()
            .filter(h -> !h.time().isBefore(item.getStartTime()) && h.time().isBefore(item.getEndTime()))
            .filter(this::isBadWeather)
            .toList();
    if (badHours.isEmpty()) {
      return java.util.Optional.empty();
    }

    List<WeatherImpactDtos.Suggestion> suggestions = new ArrayList<>();
    findShiftTime(item, date).ifPresent(suggestions::add);
    findChangeDay(item, allDays, plan).ifPresent(suggestions::add);
    findSwapPlace(item).ifPresent(suggestions::add);

    WeatherDtos.HourlyForecast worst = badHours.get(0);
    return java.util.Optional.of(
        new WeatherImpactDtos.ImpactedItem(
            item.getItemId(),
            day.getDayId(),
            day.getDayNumber(),
            date,
            item.getPlace().getPlaceId(),
            item.getPlace().getName(),
            item.getStartTime(),
            item.getEndTime(),
            worst.weatherCode(),
            worst.condition(),
            WARNING_MESSAGE,
            suggestions));
  }

  private boolean isBadWeather(WeatherDtos.HourlyForecast hour) {
    return isBadWeather(hour, properties);
  }

  /** GBR-48: xác suất mưa cao + lượng mưa lớn, HOẶC mã thời tiết thuộc danh sách giông/bão. */
  static boolean isBadWeather(WeatherDtos.HourlyForecast hour, WeatherProperties properties) {
    boolean heavyRain =
        hour.precipitationProbability() != null
            && hour.precipitationProbability() >= properties.heavyRainProbabilityOrDefault()
            && hour.precipitation() != null
            && hour.precipitation().doubleValue() >= properties.heavyRainMmPerHourOrDefault();
    boolean storm =
        hour.weatherCode() != null
            && properties.stormWeatherCodesOrDefault().contains(hour.weatherCode());
    return heavyRain || storm;
  }

  /** Same day, same duration, a start time in 06:00-21:00 with no bad-weather hour in its span. */
  private java.util.Optional<WeatherImpactDtos.Suggestion> findShiftTime(ItineraryItem item, LocalDate date) {
    long durationMinutes = java.time.Duration.between(item.getStartTime(), item.getEndTime()).toMinutes();
    if (durationMinutes <= 0) {
      return java.util.Optional.empty();
    }
    WeatherDtos.WeatherResponse forecast = weatherService.forecast(item.getPlace(), date, date);
    for (LocalTime candidate = DAY_WINDOW_START;
        !candidate.plusMinutes(durationMinutes).isAfter(DAY_WINDOW_END);
        candidate = candidate.plusHours(1)) {
      LocalTime candidateEnd = candidate.plusMinutes(durationMinutes);
      LocalTime finalCandidate = candidate;
      boolean clear =
          forecast.hourly().stream()
              .filter(h -> !h.time().isBefore(finalCandidate) && h.time().isBefore(candidateEnd))
              .noneMatch(this::isBadWeather);
      if (clear) {
        return java.util.Optional.of(
            new WeatherImpactDtos.Suggestion(
                WeatherImpactDtos.SuggestionType.SHIFT_TIME,
                "Đổi giờ trong ngày sang " + candidate,
                date,
                candidate,
                candidateEnd,
                null,
                null));
      }
    }
    return java.util.Optional.empty();
  }

  /** Another day already in this itinerary, same time window, no bad-weather hour. */
  private java.util.Optional<WeatherImpactDtos.Suggestion> findChangeDay(
      ItineraryItem item, List<ItineraryDay> allDays, TravelPlan plan) {
    for (ItineraryDay other : allDays) {
      if (other.getDayId().equals(item.getDay().getDayId())) {
        continue;
      }
      LocalDate otherDate = plan.getStartDate().plusDays(other.getDayNumber() - 1L);
      WeatherDtos.WeatherResponse forecast = weatherService.forecast(item.getPlace(), otherDate, otherDate);
      if (!forecast.notAvailable().isEmpty()) {
        continue;
      }
      boolean clear =
          forecast.hourly().stream()
              .filter(h -> !h.time().isBefore(item.getStartTime()) && h.time().isBefore(item.getEndTime()))
              .noneMatch(this::isBadWeather);
      if (clear) {
        return java.util.Optional.of(
            new WeatherImpactDtos.Suggestion(
                WeatherImpactDtos.SuggestionType.CHANGE_DAY,
                "Đổi sang ngày " + other.getDayNumber(),
                otherDate,
                item.getStartTime(),
                item.getEndTime(),
                null,
                null));
      }
    }
    return java.util.Optional.empty();
  }

  /**
   * Nearest same-province indoor place as a swap candidate. No real distance ranking (no PostGIS
   * function wired up here) — just "ACTIVE, indoor, same province, not the current place", first
   * match by id. Good enough for an MVP suggestion, not a precise "nearest" guarantee.
   */
  private java.util.Optional<WeatherImpactDtos.Suggestion> findSwapPlace(ItineraryItem item) {
    Place current = item.getPlace();
    if (current.getProvince() == null) {
      return java.util.Optional.empty();
    }
    Long provinceId = current.getProvince().getProvinceId();
    Long currentPlaceId = current.getPlaceId();

    Specification<Place> spec =
        (root, query, cb) ->
            cb.and(
                cb.equal(root.get("status"), "ACTIVE"),
                cb.equal(root.join("province", JoinType.LEFT).get("provinceId"), provinceId),
                cb.equal(root.join("category", JoinType.LEFT).get("isOutdoor"), false),
                cb.notEqual(root.get("placeId"), currentPlaceId));

    List<Place> candidates =
        placeRepository.findAll(spec, PageRequest.of(0, NEARBY_INDOOR_LIMIT)).getContent();
    if (candidates.isEmpty()) {
      return java.util.Optional.empty();
    }
    Place candidate = candidates.get(0);
    return java.util.Optional.of(
        new WeatherImpactDtos.Suggestion(
            WeatherImpactDtos.SuggestionType.SWAP_PLACE,
            "Đổi sang địa điểm trong nhà gần đó: " + candidate.getName(),
            null,
            null,
            null,
            candidate.getPlaceId(),
            candidate.getName()));
  }

  private Itinerary requireOwnedItinerary(Long userId, Long itineraryId) {
    Itinerary itinerary =
        itineraryRepository
            .findById(itineraryId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ITINERARY_NOT_FOUND));
    if (!itinerary.getPlan().getUser().getUserId().equals(userId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, ITINERARY_NOT_FOUND);
    }
    return itinerary;
  }

  private static WeatherImpactDtos.SuggestionType parseSuggestionType(String value) {
    try {
      return WeatherImpactDtos.SuggestionType.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, UNKNOWN_SUGGESTION_TYPE);
    }
  }

  private static void requireTimes(WeatherImpactDtos.ApplyRequest request) {
    if (request.newStartTime() == null || request.newEndTime() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, MISSING_SUGGESTION_FIELDS);
    }
  }

  private static ItineraryItemDtos.UpdateRequest updateRequestFor(
      ItineraryItem item, LocalTime startTime, LocalTime endTime, Long placeId) {
    return new ItineraryItemDtos.UpdateRequest(
        placeId,
        startTime,
        endTime,
        item.getEstimatedCost(),
        item.getTransportMode() == null ? null : item.getTransportMode().getTransportModeId(),
        item.getTravelTimeMinutes(),
        item.getNotes(),
        null);
  }
}
