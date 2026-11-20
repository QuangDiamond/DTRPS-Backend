package com.dtrps.modules.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.controller.AdminTripController;
import com.dtrps.modules.itinerary.dto.AdminTripDtos;
import com.dtrps.modules.itinerary.entity.ActivityProgress;
import com.dtrps.modules.itinerary.entity.Itinerary;
import com.dtrps.modules.itinerary.entity.ItineraryDay;
import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.entity.TripDayProgress;
import com.dtrps.modules.itinerary.entity.TripProgress;
import com.dtrps.modules.itinerary.repository.ActivityProgressRepository;
import com.dtrps.modules.itinerary.repository.AdminTripRepository;
import com.dtrps.modules.itinerary.repository.ItineraryDayRepository;
import com.dtrps.modules.itinerary.repository.TripDayProgressRepository;
import com.dtrps.modules.itinerary.repository.TripFeedbackRepository;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.user.entity.User;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

/** Unit test AdminTripService — sheet Itinerary, UTC001–UTC006 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class AdminTripServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Pageable PAGE = PageRequest.of(0, 20);
  private static final Long PLAN_ID = 1L;
  private static final Long ITINERARY_ID = 10L;
  private static final Long PROGRESS_ID = 20L;

  @Mock private AdminTripRepository trips;
  @Mock private ItineraryDayRepository days;
  @Mock private TripDayProgressRepository dayProgress;
  @Mock private ActivityProgressRepository activityProgress;
  @Mock private TripFeedbackRepository feedback;
  @Mock private PlaceImageRepository images;

  private AdminTripService service;
  private TravelPlan plan;
  private Itinerary itinerary;

  @BeforeEach
  void setUp() {
    service =
        new AdminTripService(
            trips,
            days,
            dayProgress,
            activityProgress,
            feedback,
            images,
            Clock.fixed(NOW, ZoneOffset.UTC));
    User traveller = new User();
    traveller.setUserId(7L);
    traveller.setFullName("Nguyễn Văn A");
    traveller.setEmail("a@gmail.com");
    plan = new TravelPlan();
    plan.setPlanId(PLAN_ID);
    plan.setTitle("Đà Nẵng 4 ngày");
    plan.setDestination("Đà Nẵng");
    plan.setStartDate(LocalDate.of(2026, 11, 18));
    plan.setEndDate(LocalDate.of(2026, 11, 21));
    plan.setStatus("IN_PROGRESS");
    plan.setUser(traveller);

    itinerary = new Itinerary();
    itinerary.setItineraryId(ITINERARY_ID);
    itinerary.setPlan(plan);
    itinerary.setStatus("ACTIVE");
    itinerary.setVersionNumber(1);
    TripProgress progress = new TripProgress();
    progress.setProgressId(PROGRESS_ID);
    progress.setItinerary(itinerary);

    lenient().when(trips.itinerariesOf(any())).thenReturn(List.of(itinerary));
    lenient().when(trips.dayCounts(any())).thenReturn(rows(new Object[] {ITINERARY_ID, 4L}));
    lenient().when(trips.stopCounts(any())).thenReturn(rows(new Object[] {ITINERARY_ID, 12L}));
    lenient().when(trips.progressOf(any())).thenReturn(List.of(progress));
    lenient()
        .when(trips.activityCounts(any()))
        .thenReturn(rows(new Object[] {PROGRESS_ID, "COMPLETED", 5L}));
  }

  private static List<Object[]> rows(Object[]... rows) {
    return new ArrayList<>(List.of(rows));
  }

  private void dayStates(Object[]... rows) {
    when(trips.dayStates(any())).thenReturn(rows(rows));
  }

  private void page(List<TravelPlan> plans) {
    when(trips.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(plans, PAGE, plans.size()));
  }

  private static AdminTripService.TripFilter filter(String status, String destination) {
    return new AdminTripService.TripFilter(null, status, destination, null, null);
  }

  @Nested
  @DisplayName("list")
  class ListTrips {
    @Test
    @DisplayName("UTC001 Trip in progress in Đà Nẵng → \"day 2/4 · 5/12 stops\" from real progress")
    void inProgress_progress() {
      page(List.of(plan));
      dayStates(
          new Object[] {PROGRESS_ID, 1, "COMPLETED"}, new Object[] {PROGRESS_ID, 2, "IN_PROGRESS"});

      PageResponse<AdminTripDtos.TripSummary> result =
          service.list(filter("IN_PROGRESS", "Đà Nẵng"), PAGE);

      AdminTripDtos.Progress progress = result.items().get(0).progress();
      assertThat(progress.currentDay()).isEqualTo(2);
      assertThat(progress.totalDays()).isEqualTo(4);
      assertThat(progress.currentDayStarted()).isTrue();
      assertThat(progress.completedStops()).isEqualTo(5);
      assertThat(progress.totalStops()).isEqualTo(12);
      assertThat(result.items().get(0).traveller().name()).isEqualTo("Nguyễn Văn A");
      assertThat(result.items().get(0).overdue()).isFalse();
    }

    @Test
    @DisplayName("UTC002 status=\"ABC\" → 400 (validation on AdminTripController.list)")
    void unknownStatus_invalid() throws Exception {
      ExecutableValidator validator =
          Validation.buildDefaultValidatorFactory().getValidator().forExecutables();
      Method list =
          AdminTripController.class.getMethod(
              "list",
              String.class,
              String.class,
              String.class,
              LocalDate.class,
              LocalDate.class,
              String.class,
              int.class,
              int.class);
      Object[] args = {null, "ABC", null, null, null, null, 0, 20};

      Set<String> messages =
          validator.validateParameters(mock(AdminTripController.class), list, args).stream()
              .map(ConstraintViolation::getMessage)
              .collect(Collectors.toSet());

      assertThat(messages).containsExactly("Trạng thái chuyến đi không hợp lệ");
      args[1] = "OVERDUE";
      assertThat(validator.validateParameters(mock(AdminTripController.class), list, args))
          .isEmpty();
    }

    @Test
    @DisplayName("UTC003 Trip on its last day → progress shows day 4/4")
    void lastDay() {
      page(List.of(plan));
      dayStates(
          new Object[] {PROGRESS_ID, 1, "COMPLETED"},
          new Object[] {PROGRESS_ID, 2, "COMPLETED"},
          new Object[] {PROGRESS_ID, 3, "COMPLETED"},
          new Object[] {PROGRESS_ID, 4, "IN_PROGRESS"});

      AdminTripDtos.Progress progress =
          service.list(filter("IN_PROGRESS", null), PAGE).items().get(0).progress();

      assertThat(progress.currentDay()).isEqualTo(4);
      assertThat(progress.completedDays()).isEqualTo(3);
    }

    @Test
    @DisplayName("UTC004 No trip matches → empty page, no progress queries")
    void noMatch_empty() {
      page(List.of());

      PageResponse<AdminTripDtos.TripSummary> result =
          service.list(new AdminTripService.TripFilter("zzz", null, null, null, null), PAGE);

      assertThat(result.items()).isEmpty();
      assertThat(result.total()).isZero();
      verify(trips, never()).itinerariesOf(any());
    }
  }

  @Nested
  @DisplayName("get")
  class Get {
    @Test
    @DisplayName("UTC005 Existing trip → day-by-day itinerary with stop status and traveller")
    void existing_detail() {
      when(trips.findById(PLAN_ID)).thenReturn(Optional.of(plan));
      dayStates(new Object[] {PROGRESS_ID, 1, "COMPLETED"});
      ItineraryDay day1 = new ItineraryDay();
      day1.setDayId(30L);
      day1.setDayNumber(1);
      day1.setTitle("Ngày 1");
      Place museum = new Place();
      museum.setPlaceId(101L);
      museum.setName("Bảo tàng Chăm");
      ItineraryItem stop = new ItineraryItem();
      stop.setItemId(40L);
      stop.setItemOrder(1);
      stop.setDay(day1);
      stop.setPlace(museum);
      when(days.findByItineraryItineraryIdOrderByDayNumberAsc(ITINERARY_ID))
          .thenReturn(List.of(day1));
      when(trips.stopsOf(ITINERARY_ID)).thenReturn(List.of(stop));
      TripDayProgress dayState = new TripDayProgress();
      dayState.setDay(day1);
      dayState.setStatus("COMPLETED");
      when(dayProgress.findByProgressProgressIdOrderByDayDayNumberAsc(PROGRESS_ID))
          .thenReturn(List.of(dayState));
      ActivityProgress stopState = new ActivityProgress();
      stopState.setItem(stop);
      stopState.setStatus("COMPLETED");
      when(activityProgress.findOrderedByProgressId(PROGRESS_ID)).thenReturn(List.of(stopState));

      AdminTripDtos.TripDetail detail = service.get(PLAN_ID);

      assertThat(detail.summary().traveller().email()).isEqualTo("a@gmail.com");
      assertThat(detail.days()).hasSize(1);
      AdminTripDtos.Day first = detail.days().get(0);
      assertThat(first.date()).isEqualTo(LocalDate.of(2026, 11, 18));
      assertThat(first.status()).isEqualTo("COMPLETED");
      assertThat(first.stops())
          .extracting(AdminTripDtos.Stop::placeName)
          .containsExactly("Bảo tàng Chăm");
      assertThat(first.stops().get(0).status()).isEqualTo("COMPLETED");
      assertThat(detail.feedback()).isNull();
    }

    @Test
    @DisplayName("UTC006 Unknown trip → 404")
    void missing_notFound() {
      ApiException e = catchThrowableOfType(() -> service.get(999_999L), ApiException.class);

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy chuyến đi");
    }
  }
}
