package com.dtrps.modules.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import com.dtrps.modules.itinerary.entity.PlanPlace;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceOpeningHour;
import com.dtrps.modules.poi.repository.PlaceOpeningHourRepository;
import com.dtrps.modules.poi.repository.PlacePriceRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Bộ xếp lịch không chặn người dùng: luôn xếp đủ địa điểm, chỉ trả cảnh báo. */
@ExtendWith(MockitoExtension.class)
class ItinerarySchedulingServiceTest {

  @Mock private PlaceOpeningHourRepository openingHours;
  @Mock private PlacePriceRepository prices;

  private ItinerarySchedulingService scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new ItinerarySchedulingService(openingHours, prices);
    lenient().when(openingHours.findByPlacePlaceIdOrderByDayOfWeekAsc(anyLong())).thenReturn(List.of());
    lenient().when(prices.findByPlacePlaceIdOrderByPriceIdAsc(anyLong())).thenReturn(List.of());
  }

  private static TravelPlan plan(int days, String pace) {
    TravelPlan plan = new TravelPlan();
    plan.setStartDate(LocalDate.of(2026, 12, 1));
    plan.setEndDate(LocalDate.of(2026, 12, days));
    plan.setTravelPace(pace);
    plan.setGroupSize(2);
    return plan;
  }

  private static List<PlanPlace> places(int count) {
    List<PlanPlace> rows = new ArrayList<>();
    for (long i = 1; i <= count; i++) {
      Place place = new Place();
      place.setPlaceId(i);
      place.setName("Điểm " + i);
      place.setStatus("ACTIVE");
      PlanPlace row = new PlanPlace();
      row.setPlanPlaceId(i);
      row.setPlace(place);
      rows.add(row);
    }
    return rows;
  }

  private static long total(ItinerarySchedulingService.Schedule schedule) {
    return schedule.days().stream().mapToLong(List::size).sum();
  }

  @Test
  @DisplayName("Nhiều địa điểm hơn sức chứa: vẫn xếp đủ, kèm cảnh báo và ghi chú ngoài giờ")
  void tooManyPlacesStillScheduled() {
    var schedule = scheduler.arrange(plan(1, "BALANCED"), places(10));

    assertThat(total(schedule)).isEqualTo(10);
    assertThat(schedule.warnings()).isNotEmpty();
    assertThat(schedule.warnings()).anyMatch(w -> w.contains("nhiều hơn nhịp độ"));
    assertThat(schedule.days().get(0)).anyMatch(v -> v.note() != null);
    // Không giờ nào tràn sang ngày hôm sau.
    assertThat(schedule.days().get(0)).allMatch(v -> !v.end().isBefore(v.start()));
  }

  @Test
  @DisplayName("Ít địa điểm hơn số ngày: tạo bình thường, cảnh báo ngày trống")
  void fewerPlacesThanDays() {
    var schedule = scheduler.arrange(plan(3, "RELAXED"), places(2));

    assertThat(total(schedule)).isEqualTo(2);
    assertThat(schedule.warnings()).anyMatch(w -> w.contains("để trống"));
  }

  @Test
  @DisplayName("Địa điểm đóng cửa cả chuyến: vẫn được xếp với ghi chú để người dùng chỉnh")
  void closedPlaceStillScheduled() {
    PlaceOpeningHour closed = new PlaceOpeningHour();
    closed.setDayOfWeek((short) LocalDate.of(2026, 12, 1).getDayOfWeek().getValue());
    closed.setIsClosed(true);
    lenient().when(openingHours.findByPlacePlaceIdOrderByDayOfWeekAsc(2L)).thenReturn(List.of(closed));

    var schedule = scheduler.arrange(plan(1, "BALANCED"), places(2));

    assertThat(total(schedule)).isEqualTo(2);
    assertThat(schedule.days().get(0))
        .anyMatch(v -> v.place().getPlaceId() == 2L && v.note() != null);
    assertThat(schedule.warnings()).anyMatch(w -> w.contains("Điểm 2"));
  }

  @Test
  @DisplayName("Trường hợp bình thường: không có cảnh báo, không ghi chú")
  void normalCaseHasNoWarnings() {
    var schedule = scheduler.arrange(plan(2, "BALANCED"), places(4));

    assertThat(total(schedule)).isEqualTo(4);
    assertThat(schedule.warnings()).isEmpty();
    assertThat(schedule.days()).allSatisfy(day -> assertThat(day).allMatch(v -> v.note() == null));
    assertThat(schedule.days().get(0).get(0).start()).isEqualTo(LocalTime.of(9, 0));
  }
}
