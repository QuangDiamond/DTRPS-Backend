package com.dtrps.modules.itinerary.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.AdminTripDtos;
import com.dtrps.modules.itinerary.entity.ActivityProgress;
import com.dtrps.modules.itinerary.entity.Itinerary;
import com.dtrps.modules.itinerary.entity.ItineraryDay;
import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.entity.PlanPlace;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.entity.TripDayProgress;
import com.dtrps.modules.itinerary.entity.TripProgress;
import com.dtrps.modules.itinerary.repository.ActivityProgressRepository;
import com.dtrps.modules.itinerary.repository.AdminTripRepository;
import com.dtrps.modules.itinerary.repository.ItineraryDayRepository;
import com.dtrps.modules.itinerary.repository.TripDayProgressRepository;
import com.dtrps.modules.itinerary.repository.TripFeedbackRepository;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.user.entity.User;
import jakarta.persistence.criteria.Join;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý chuyến đi của admin — chỉ đọc. Tiến độ lấy từ bản ghi thực tế (trip_day_progress,
 * activity_progress), không suy từ ngày trên lịch vì khách có thể bắt đầu muộn hơn kế hoạch. Không
 * có dữ liệu vị trí GPS: hệ thống không lưu vị trí của khách.
 */
@Service
public class AdminTripService {
  /** Nhóm lọc: PLANNING = DRAFT + PLANNED; OVERDUE = đang đi nhưng đã qua ngày về dự kiến. */
  public static final String FILTER_PLANNING = "PLANNING";

  public static final String FILTER_OVERDUE = "OVERDUE";

  static final String TRIP_NOT_FOUND = "Không tìm thấy chuyến đi";

  private static final String ACTIVE = "ACTIVE";
  private static final String NOT_STARTED = "NOT_STARTED";
  private static final String IN_PROGRESS = "IN_PROGRESS";
  private static final String COMPLETED = "COMPLETED";
  private static final String SKIPPED = "SKIPPED";
  private static final String PENDING = "PENDING";
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

  private final AdminTripRepository trips;
  private final ItineraryDayRepository dayRepository;
  private final TripDayProgressRepository dayProgressRepository;
  private final ActivityProgressRepository activityProgressRepository;
  private final TripFeedbackRepository feedbackRepository;
  private final PlaceImageRepository imageRepository;
  private final Clock clock;

  public AdminTripService(
      AdminTripRepository trips,
      ItineraryDayRepository dayRepository,
      TripDayProgressRepository dayProgressRepository,
      ActivityProgressRepository activityProgressRepository,
      TripFeedbackRepository feedbackRepository,
      PlaceImageRepository imageRepository,
      Clock clock) {
    this.trips = trips;
    this.dayRepository = dayRepository;
    this.dayProgressRepository = dayProgressRepository;
    this.activityProgressRepository = activityProgressRepository;
    this.feedbackRepository = feedbackRepository;
    this.imageRepository = imageRepository;
    this.clock = clock;
  }

  /**
   * {@code keyword}: tên chuyến, điểm đến, tên / email khách. {@code from}..{@code to}: chuyến có
   * thời gian đi giao với khoảng này.
   */
  public record TripFilter(
      String keyword, String status, String destination, LocalDate from, LocalDate to) {}

  @Transactional(readOnly = true)
  public PageResponse<AdminTripDtos.TripSummary> list(TripFilter filter, Pageable pageable) {
    Page<TravelPlan> page = trips.findAll(specification(filter), pageable);
    List<TravelPlan> plans = page.getContent();
    Map<Long, Snapshot> snapshots = snapshots(plans);
    List<AdminTripDtos.TripSummary> items =
        plans.stream().map(plan -> toSummary(plan, snapshots.get(plan.getPlanId()))).toList();
    return new PageResponse<>(items, page.getTotalElements(), page.getNumber(), page.getSize());
  }

  @Transactional(readOnly = true)
  public AdminTripDtos.Stats stats() {
    Map<String, Long> counts =
        trips.countGroupedByStatus().stream()
            .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    long total = counts.values().stream().mapToLong(Long::longValue).sum();
    return new AdminTripDtos.Stats(
        total,
        counts.getOrDefault(TravelPlanStatus.DRAFT, 0L)
            + counts.getOrDefault(TravelPlanStatus.PLANNED, 0L),
        counts.getOrDefault(TravelPlanStatus.IN_PROGRESS, 0L),
        trips.countOverdue(today()),
        counts.getOrDefault(TravelPlanStatus.COMPLETED, 0L),
        counts.getOrDefault(TravelPlanStatus.CANCELLED, 0L));
  }

  @Transactional(readOnly = true)
  public List<String> destinations() {
    return trips.destinations();
  }

  @Transactional(readOnly = true)
  public AdminTripDtos.TripDetail get(Long planId) {
    TravelPlan plan =
        trips
            .findById(planId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, TRIP_NOT_FOUND));
    Snapshot snapshot = snapshots(List.of(plan)).get(planId);
    Itinerary itinerary = snapshot.itinerary();

    List<AdminTripDtos.Day> days =
        itinerary == null ? List.of() : days(plan, itinerary, snapshot.progress());
    List<PlanPlace> selected = trips.selectedPlaces(planId);
    Map<Long, String> covers =
        covers(selected.stream().map(item -> item.getPlace().getPlaceId()).toList());
    AdminTripDtos.Feedback feedback =
        feedbackRepository
            .findByPlanPlanIdAndUserUserId(planId, plan.getUser().getUserId())
            .map(
                found ->
                    new AdminTripDtos.Feedback(
                        found.getRating(), found.getComment(), found.getCreatedAt()))
            .orElse(null);

    return new AdminTripDtos.TripDetail(
        toSummary(plan, snapshot),
        plan.getDescription(),
        plan.getTravelPace(),
        itinerary == null ? null : itinerary.getTitle(),
        itinerary == null ? null : itinerary.getVersionNumber(),
        days,
        selected.stream()
            .map(
                item ->
                    new AdminTripDtos.SelectedPlace(
                        item.getPlace().getPlaceId(),
                        item.getPlace().getName(),
                        item.getPlace().getAddress(),
                        covers.get(item.getPlace().getPlaceId())))
            .toList(),
        feedback);
  }

  // ===== Bộ lọc =====

  private Specification<TravelPlan> specification(TripFilter filter) {
    Specification<TravelPlan> spec = (root, query, cb) -> cb.conjunction();
    if (hasText(filter.keyword())) {
      String like = "%" + filter.keyword().trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) -> {
                Join<TravelPlan, User> user = root.join("user");
                return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("destination")), like),
                    cb.like(cb.lower(user.get("fullName")), like),
                    cb.like(cb.lower(user.get("email")), like));
              });
    }
    if (hasText(filter.status())) {
      spec = spec.and(statusFilter(filter.status()));
    }
    if (hasText(filter.destination())) {
      String destination = filter.destination().trim();
      spec = spec.and((root, query, cb) -> cb.equal(root.get("destination"), destination));
    }
    if (filter.from() != null) {
      LocalDate from = filter.from();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("endDate"), from));
    }
    if (filter.to() != null) {
      LocalDate to = filter.to();
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("startDate"), to));
    }
    return spec;
  }

  private Specification<TravelPlan> statusFilter(String status) {
    if (FILTER_PLANNING.equals(status)) {
      return (root, query, cb) ->
          root.get("status").in(TravelPlanStatus.DRAFT, TravelPlanStatus.PLANNED);
    }
    if (FILTER_OVERDUE.equals(status)) {
      LocalDate today = today();
      return (root, query, cb) ->
          cb.and(
              cb.equal(root.get("status"), TravelPlanStatus.IN_PROGRESS),
              cb.lessThan(root.<LocalDate>get("endDate"), today));
    }
    return (root, query, cb) -> cb.equal(root.get("status"), status);
  }

  // ===== Tiến độ =====

  /** Lịch trình đang dùng + tiến độ của một chuyến. */
  private record Snapshot(
      Itinerary itinerary, TripProgress progress, AdminTripDtos.Progress summary) {}

  /** Tiến độ của cả trang trong vài truy vấn gom, không truy vấn theo từng chuyến. */
  private Map<Long, Snapshot> snapshots(List<TravelPlan> plans) {
    if (plans.isEmpty()) {
      return new HashMap<>();
    }
    Map<Long, Itinerary> itineraries =
        currentItineraries(trips.itinerariesOf(plans.stream().map(TravelPlan::getPlanId).toList()));
    List<Long> itineraryIds = itineraries.values().stream().map(Itinerary::getItineraryId).toList();

    // Dùng HashMap vì khoá có thể null (chuyến chưa có lịch trình / chưa bắt đầu)
    Map<Long, Long> dayCounts = new HashMap<>();
    Map<Long, Long> stopCounts = new HashMap<>();
    Map<Long, TripProgress> progressByItinerary = new HashMap<>();
    if (!itineraryIds.isEmpty()) {
      dayCounts.putAll(counts(trips.dayCounts(itineraryIds)));
      stopCounts.putAll(counts(trips.stopCounts(itineraryIds)));
      trips
          .progressOf(itineraryIds)
          .forEach(
              progress ->
                  progressByItinerary.put(progress.getItinerary().getItineraryId(), progress));
    }
    List<Long> progressIds =
        progressByItinerary.values().stream().map(TripProgress::getProgressId).toList();

    Map<Long, List<Object[]>> dayStates = new HashMap<>();
    Map<Long, List<Object[]>> activities = new HashMap<>();
    if (!progressIds.isEmpty()) {
      dayStates.putAll(
          trips.dayStates(progressIds).stream()
              .collect(Collectors.groupingBy(row -> (Long) row[0])));
      activities.putAll(
          trips.activityCounts(progressIds).stream()
              .collect(Collectors.groupingBy(row -> (Long) row[0])));
    }

    Map<Long, Snapshot> result = new HashMap<>();
    for (TravelPlan plan : plans) {
      Itinerary itinerary = itineraries.get(plan.getPlanId());
      Long itineraryId = itinerary == null ? null : itinerary.getItineraryId();
      TripProgress progress = progressByItinerary.get(itineraryId);
      Long progressId = progress == null ? null : progress.getProgressId();
      result.put(
          plan.getPlanId(),
          new Snapshot(
              itinerary,
              progress,
              summarize(
                  plan,
                  dayCounts.getOrDefault(itineraryId, 0L).intValue(),
                  stopCounts.getOrDefault(itineraryId, 0L).intValue(),
                  dayStates.getOrDefault(progressId, List.of()),
                  activities.getOrDefault(progressId, List.of()))));
    }
    return result;
  }

  /** Lịch trình ACTIVE mới nhất; chưa có thì lấy phiên bản mới nhất. */
  private static Map<Long, Itinerary> currentItineraries(List<Itinerary> itineraries) {
    Comparator<Itinerary> preference =
        Comparator.<Itinerary, Boolean>comparing(item -> ACTIVE.equals(item.getStatus()))
            .thenComparing(Itinerary::getVersionNumber);
    return itineraries.stream()
        .collect(
            Collectors.toMap(
                item -> item.getPlan().getPlanId(),
                Function.identity(),
                (first, second) -> preference.compare(first, second) >= 0 ? first : second));
  }

  private static AdminTripDtos.Progress summarize(
      TravelPlan plan,
      int totalDays,
      int totalStops,
      List<Object[]> dayStates,
      List<Object[]> activities) {
    int completedDays = 0;
    int lastCompleted = 0;
    Integer runningDay = null;
    for (Object[] row : dayStates) {
      int dayNumber = (Integer) row[1];
      if (COMPLETED.equals(row[2])) {
        completedDays++;
        lastCompleted = Math.max(lastCompleted, dayNumber);
      } else if (IN_PROGRESS.equals(row[2])) {
        runningDay = dayNumber;
      }
    }
    int completedStops = activityCount(activities, COMPLETED);
    int skippedStops = activityCount(activities, SKIPPED);

    Integer currentDay = null;
    if (TravelPlanStatus.IN_PROGRESS.equals(plan.getStatus()) && totalDays > 0) {
      currentDay = runningDay != null ? runningDay : Math.min(lastCompleted + 1, totalDays);
    }
    int percent = 0;
    if (totalStops > 0) {
      percent = (completedStops + skippedStops) * 100 / totalStops;
    } else if (totalDays > 0) {
      percent = completedDays * 100 / totalDays;
    }
    return new AdminTripDtos.Progress(
        totalDays,
        completedDays,
        currentDay,
        runningDay != null,
        totalStops,
        completedStops,
        skippedStops,
        percent);
  }

  private static int activityCount(List<Object[]> activities, String status) {
    return activities.stream()
        .filter(row -> status.equals(row[1]))
        .mapToInt(row -> ((Long) row[2]).intValue())
        .sum();
  }

  // ===== Chi tiết lịch trình =====

  private List<AdminTripDtos.Day> days(
      TravelPlan plan, Itinerary itinerary, TripProgress progress) {
    Map<Long, TripDayProgress> dayStates = new HashMap<>();
    Map<Long, ActivityProgress> stopStates = new HashMap<>();
    if (progress != null) {
      dayProgressRepository
          .findByProgressProgressIdOrderByDayDayNumberAsc(progress.getProgressId())
          .forEach(state -> dayStates.put(state.getDay().getDayId(), state));
      activityProgressRepository
          .findOrderedByProgressId(progress.getProgressId())
          .forEach(state -> stopStates.put(state.getItem().getItemId(), state));
    }

    List<ItineraryItem> stops = trips.stopsOf(itinerary.getItineraryId());
    Map<Long, String> covers =
        covers(stops.stream().map(stop -> stop.getPlace().getPlaceId()).toList());
    Map<Long, List<ItineraryItem>> stopsByDay =
        stops.stream().collect(Collectors.groupingBy(stop -> stop.getDay().getDayId()));

    List<AdminTripDtos.Day> result = new ArrayList<>();
    for (ItineraryDay day :
        dayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itinerary.getItineraryId())) {
      Optional<TripDayProgress> state = Optional.ofNullable(dayStates.get(day.getDayId()));
      result.add(
          new AdminTripDtos.Day(
              day.getDayNumber(),
              plan.getStartDate().plusDays(day.getDayNumber() - 1L),
              day.getTitle(),
              state.map(TripDayProgress::getStatus).orElse(NOT_STARTED),
              state.map(TripDayProgress::getStartedAt).orElse(null),
              state.map(TripDayProgress::getCompletedAt).orElse(null),
              stopsByDay.getOrDefault(day.getDayId(), List.of()).stream()
                  .map(stop -> toStop(stop, stopStates.get(stop.getItemId()), covers))
                  .toList()));
    }
    return result;
  }

  private static AdminTripDtos.Stop toStop(
      ItineraryItem stop, ActivityProgress state, Map<Long, String> covers) {
    return new AdminTripDtos.Stop(
        stop.getItemId(),
        stop.getItemOrder(),
        stop.getPlace().getPlaceId(),
        stop.getPlace().getName(),
        stop.getPlace().getAddress(),
        covers.get(stop.getPlace().getPlaceId()),
        stop.getStartTime(),
        stop.getEndTime(),
        stop.getEstimatedCost(),
        state == null ? PENDING : state.getStatus(),
        state == null ? null : state.getCompletedAt(),
        state == null ? null : state.getNotes());
  }

  /** Ảnh đại diện của từng địa điểm (ảnh chính đứng đầu kết quả truy vấn). */
  private Map<Long, String> covers(Collection<Long> placeIds) {
    Map<Long, String> covers = new HashMap<>();
    if (placeIds.isEmpty()) {
      return covers;
    }
    for (PlaceImage image : imageRepository.findForPlaces(placeIds)) {
      covers.putIfAbsent(image.getPlace().getPlaceId(), image.getImageUrl());
    }
    return covers;
  }

  // ===== Helpers =====

  private AdminTripDtos.TripSummary toSummary(TravelPlan plan, Snapshot snapshot) {
    User user = plan.getUser();
    TripProgress progress = snapshot.progress();
    return new AdminTripDtos.TripSummary(
        plan.getPlanId(),
        plan.getTitle(),
        plan.getDestination(),
        plan.getStartDate(),
        plan.getEndDate(),
        plan.getGroupSize(),
        plan.getBudget(),
        plan.getCurrency(),
        plan.getStatus(),
        TravelPlanStatus.IN_PROGRESS.equals(plan.getStatus())
            && plan.getEndDate().isBefore(today()),
        new AdminTripDtos.Traveller(
            user.getUserId(), user.getFullName(), user.getEmail(), user.getAvatarUrl()),
        snapshot.summary(),
        progress == null ? null : progress.getStartedAt(),
        progress == null ? null : progress.getCompletedAt(),
        plan.getCreatedAt());
  }

  private static Map<Long, Long> counts(List<Object[]> rows) {
    return rows.stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), VIETNAM);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
