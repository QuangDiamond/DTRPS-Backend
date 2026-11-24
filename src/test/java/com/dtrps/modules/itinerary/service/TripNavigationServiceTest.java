package com.dtrps.modules.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.TripNavigationDtos.NextRouteResponse;
import com.dtrps.modules.itinerary.entity.ActivityProgress;
import com.dtrps.modules.itinerary.entity.Itinerary;
import com.dtrps.modules.itinerary.entity.ItineraryDay;
import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.entity.TripDayProgress;
import com.dtrps.modules.itinerary.entity.TripProgress;
import com.dtrps.modules.itinerary.repository.ActivityProgressRepository;
import com.dtrps.modules.itinerary.repository.ItineraryDayRepository;
import com.dtrps.modules.itinerary.repository.ItineraryItemRepository;
import com.dtrps.modules.itinerary.repository.ItineraryRepository;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.itinerary.repository.TripDayProgressRepository;
import com.dtrps.modules.itinerary.repository.TripProgressRepository;
import com.dtrps.modules.maps.dto.RouteResponse;
import com.dtrps.modules.maps.dto.RouteStep;
import com.dtrps.modules.maps.service.MapService;
import com.dtrps.modules.poi.entity.Place;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Unit test TripNavigationService.getNextRoute — sheet Itinerary, UTC051–UTC072. */
@ExtendWith(MockitoExtension.class)
class TripNavigationServiceTest {
  private static final long USER = 10L;
  private static final long PLAN = 100L;
  private static final long ITINERARY = 7L;
  private static final long DAY = 70L;
  private static final long PROGRESS = 5L;

  /** Địa điểm tiếp theo: Cầu Rồng, Đà Nẵng. */
  private static final double STOP_LAT = 16.0544;

  private static final double STOP_LNG = 108.2022;

  /** 1 độ vĩ ≈ 111.195 m, dùng để đặt GPS cách điểm đến một khoảng cho trước. */
  private static final double METERS_PER_DEGREE = 111_195.0;

  @Mock private TravelPlanRepository planRepository;
  @Mock private ItineraryRepository itineraryRepository;
  @Mock private ItineraryDayRepository dayRepository;
  @Mock private ItineraryItemRepository itemRepository;
  @Mock private TripProgressRepository tripProgressRepository;
  @Mock private TripDayProgressRepository dayProgressRepository;
  @Mock private ActivityProgressRepository activityProgressRepository;
  @Mock private MapService mapService;

  private TripNavigationService service;
  private ItineraryItem item1;
  private ItineraryItem item2;
  private final RouteResponse goongRoute =
      new RouteResponse(
          3000,
          600,
          "3 km",
          "10 phút",
          "poly",
          List.of(
              new RouteStep(
                  "Rẽ phải", "turn-right", 300, 60, "300 m", 16.05, 108.2, 16.051, 108.201)));

  @BeforeEach
  void setUp() {
    service =
        new TripNavigationService(
            planRepository,
            itineraryRepository,
            dayRepository,
            itemRepository,
            tripProgressRepository,
            dayProgressRepository,
            activityProgressRepository,
            mapService);
    happyPath();
  }

  /** Chuyến đi đang diễn ra, ngày 1 đã bắt đầu, 2 điểm đều PENDING, Goong trả tuyến 3 km. */
  private void happyPath() {
    TravelPlan plan = new TravelPlan();
    plan.setPlanId(PLAN);
    plan.setStatus("IN_PROGRESS");
    Itinerary itinerary = new Itinerary();
    itinerary.setItineraryId(ITINERARY);
    itinerary.setPlan(plan);
    ItineraryDay day = new ItineraryDay();
    day.setDayId(DAY);
    day.setDayNumber(1);
    day.setItinerary(itinerary);
    TripProgress progress = new TripProgress();
    progress.setProgressId(PROGRESS);
    progress.setStatus("IN_PROGRESS");
    TripDayProgress dayProgress = new TripDayProgress();
    dayProgress.setStatus("IN_PROGRESS");

    item1 = item(501L, 1, STOP_LAT, STOP_LNG);
    item2 = item(502L, 2, 16.0600, 108.2100);
    item1.setDay(day);
    item2.setDay(day);

    lenient()
        .when(planRepository.findByPlanIdAndUserUserId(PLAN, USER))
        .thenReturn(Optional.of(plan));
    lenient()
        .when(
            itineraryRepository.findFirstByPlanPlanIdAndStatusOrderByVersionNumberDesc(
                PLAN, "ACTIVE"))
        .thenReturn(Optional.of(itinerary));
    lenient()
        .when(dayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(ITINERARY))
        .thenReturn(List.of(day));
    lenient()
        .when(tripProgressRepository.findByItineraryItineraryId(ITINERARY))
        .thenReturn(Optional.of(progress));
    lenient()
        .when(dayProgressRepository.findByProgressProgressIdAndDayDayId(PROGRESS, DAY))
        .thenReturn(Optional.of(dayProgress));
    lenient().when(itemRepository.findOrderedByDayId(DAY)).thenReturn(List.of(item1, item2));
    lenient()
        .when(activityProgressRepository.findOrderedByProgressIdAndDayId(PROGRESS, DAY))
        .thenReturn(List.of());
    lenient()
        .when(
            mapService.directions(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
        .thenReturn(goongRoute);
  }

  private static ItineraryItem item(long id, int order, Double lat, Double lng) {
    Place place = new Place();
    place.setPlaceId(id + 1000);
    place.setName("Điểm " + order);
    place.setAddress("Địa chỉ " + order);
    place.setLatitude(lat);
    place.setLongitude(lng);
    ItineraryItem item = new ItineraryItem();
    item.setItemId(id);
    item.setItemOrder(order);
    item.setPlace(place);
    return item;
  }

  private static ActivityProgress progressOf(ItineraryItem item, String status) {
    ActivityProgress progress = new ActivityProgress();
    progress.setItem(item);
    progress.setStatus(status);
    return progress;
  }

  /** GPS cách điểm đến (item 1) đúng khoảng {@code meters} về phía bắc. */
  private NextRouteResponse routeFromMetersAway(double meters, Double accuracy) {
    return service.getNextRoute(
        USER, PLAN, 1, STOP_LAT + meters / METERS_PER_DEGREE, STOP_LNG, accuracy, "car");
  }

  private ApiException error(double lat, double lng, Integer day, String vehicle) {
    return catchThrowableOfType(
        () -> service.getNextRoute(USER, PLAN, day, lat, lng, 15.0, vehicle), ApiException.class);
  }

  // ---- Normal ----

  @Test
  @DisplayName(
      "UTC051 GPS hợp lệ, điểm cách 3 km → tuyến có steps, arrived=false, lowAccuracy=false")
  void returnsRouteWithSteps() {
    NextRouteResponse response = service.getNextRoute(USER, PLAN, 1, 16.03, 108.2, 15.0, "car");

    assertThat(response.itemId()).isEqualTo(501L);
    assertThat(response.route().distanceMeters()).isEqualTo(3000);
    assertThat(response.route().steps()).hasSize(1);
    assertThat(response.arrived()).isFalse();
    assertThat(response.lowAccuracy()).isFalse();
  }

  @Test
  @DisplayName("UTC052 Điểm 1 đã hoàn thành → chỉ đường tới điểm PENDING đầu tiên là điểm 2")
  void skipsHandledStops() {
    lenient()
        .when(activityProgressRepository.findOrderedByProgressIdAndDayId(PROGRESS, DAY))
        .thenReturn(List.of(progressOf(item1, "COMPLETED")));

    NextRouteResponse response = service.getNextRoute(USER, PLAN, 1, 16.03, 108.2, 15.0, "car");

    assertThat(response.itemId()).isEqualTo(502L);
    assertThat(response.itemOrder()).isEqualTo(2);
  }

  @Test
  @DisplayName("UTC053 vehicle=bike → gọi Goong với bike và trả vehicle=bike")
  void passesVehicleToGoong() {
    NextRouteResponse response = service.getNextRoute(USER, PLAN, 1, 16.03, 108.2, 15.0, "bike");

    assertThat(response.vehicle()).isEqualTo("bike");
    verify(mapService).directions(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("bike"));
  }

  // ---- Boundary ----

  @Test
  @DisplayName("UTC054 vehicle null hoặc rỗng → mặc định car")
  void defaultVehicleIsCar() {
    assertThat(service.getNextRoute(USER, PLAN, 1, 16.03, 108.2, 15.0, null).vehicle())
        .isEqualTo("car");
    assertThat(service.getNextRoute(USER, PLAN, 1, 16.03, 108.2, 15.0, "  ").vehicle())
        .isEqualTo("car");
  }

  @Test
  @DisplayName("UTC055 Cách 30 m, accuracy null → đã đến nơi, không gọi Goong, thời gian 0")
  void arrivedDoesNotCallGoong() {
    NextRouteResponse response = routeFromMetersAway(30, null);

    assertThat(response.arrived()).isTrue();
    assertThat(response.route().durationSeconds()).isZero();
    assertThat(response.route().polyline()).isEmpty();
    assertThat(response.route().steps()).isEmpty();
    verify(mapService, never())
        .directions(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString());
  }

  @Test
  @DisplayName("UTC056 Cách 49 m, accuracy 10 → đến nơi (bán kính tối thiểu 50 m)")
  void arrivedWithinMinimumRadius() {
    assertThat(routeFromMetersAway(49, 10.0).arrived()).isTrue();
    assertThat(routeFromMetersAway(52, 10.0).arrived()).isFalse();
  }

  @Test
  @DisplayName("UTC057 Cách 120 m, accuracy 150 → chưa đến (bán kính bị kẹp ở 100 m)")
  void arrivalRadiusIsCapped() {
    NextRouteResponse response = routeFromMetersAway(120, 150.0);

    assertThat(response.arrived()).isFalse();
    verify(mapService).directions(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString());
  }

  @Test
  @DisplayName("UTC058 Cách 79 m, accuracy 80 → đến nơi (bán kính theo sai số GPS)")
  void arrivalRadiusFollowsAccuracy() {
    assertThat(routeFromMetersAway(79, 80.0).arrived()).isTrue();
  }

  @Test
  @DisplayName("UTC059 accuracy 250 → lowAccuracy=true")
  void lowAccuracyAboveThreshold() {
    assertThat(routeFromMetersAway(3000, 250.0).lowAccuracy()).isTrue();
  }

  @Test
  @DisplayName("UTC060 accuracy 200 → lowAccuracy=false (ngưỡng là lớn hơn 200 m)")
  void lowAccuracyAtThreshold() {
    assertThat(routeFromMetersAway(3000, 200.0).lowAccuracy()).isFalse();
  }

  // ---- Abnormal ----

  @Test
  @DisplayName("UTC061 lat=91 → 400")
  void invalidLatitude() {
    ApiException e = error(91, 108.2, 1, "car");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).isEqualTo("GPS hiện tại không có tọa độ hợp lệ");
  }

  @Test
  @DisplayName("UTC062 lng=-181 → 400")
  void invalidLongitude() {
    ApiException e = error(16.05, -181, 1, "car");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).isEqualTo("GPS hiện tại không có tọa độ hợp lệ");
  }

  @Test
  @DisplayName("UTC063 vehicle=plane → 400")
  void unknownVehicle() {
    ApiException e = error(16.03, 108.2, 1, "plane");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).isEqualTo("Phương tiện phải là car, bike, taxi, truck hoặc hd");
  }

  @Test
  @DisplayName("UTC064 dayNumber=0 → 400")
  void invalidDayNumber() {
    ApiException e = error(16.03, 108.2, 0, "car");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).isEqualTo("Số ngày không hợp lệ");
  }

  @Test
  @DisplayName("UTC065 dayNumber=9 không có trong lịch trình → 404")
  void dayNotInItinerary() {
    ApiException e = error(16.03, 108.2, 9, "car");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(e.getMessage()).isEqualTo("Không tìm thấy ngày trong lịch trình");
  }

  @Test
  @DisplayName("UTC066 Kế hoạch của người khác → 404")
  void planOfAnotherUser() {
    ApiException e =
        catchThrowableOfType(
            () -> service.getNextRoute(USER, 101L, 1, 16.03, 108.2, 15.0, "car"),
            ApiException.class);

    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(e.getMessage()).isEqualTo("Không tìm thấy kế hoạch du lịch");
  }

  @Test
  @DisplayName("UTC067 Kế hoạch không ở trạng thái IN_PROGRESS → 409")
  void planNotInProgress() {
    TravelPlan plan = new TravelPlan();
    plan.setPlanId(PLAN);
    plan.setStatus("PLANNED");
    lenient()
        .when(planRepository.findByPlanIdAndUserUserId(PLAN, USER))
        .thenReturn(Optional.of(plan));

    ApiException e = error(16.03, 108.2, 1, "car");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(e.getMessage()).isEqualTo("Chuyến đi chưa bắt đầu hoặc không còn đang diễn ra");
  }

  @Test
  @DisplayName("UTC068 Ngày chưa bắt đầu → 409")
  void dayNotStarted() {
    lenient()
        .when(dayProgressRepository.findByProgressProgressIdAndDayDayId(PROGRESS, DAY))
        .thenReturn(Optional.empty());

    ApiException e = error(16.03, 108.2, 1, "car");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(e.getMessage()).isEqualTo("Bạn chưa bắt đầu ngày này");
  }

  @Test
  @DisplayName("UTC069 Mọi điểm đã hoàn thành hoặc bỏ qua → 409")
  void allStopsHandled() {
    lenient()
        .when(activityProgressRepository.findOrderedByProgressIdAndDayId(PROGRESS, DAY))
        .thenReturn(List.of(progressOf(item1, "COMPLETED"), progressOf(item2, "SKIPPED")));

    ApiException e = error(16.03, 108.2, 1, "car");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(e.getMessage()).isEqualTo("Đã xử lý tất cả địa điểm; hãy hoàn thành ngày tham quan");
  }

  @Test
  @DisplayName("UTC070 Ngày không có địa điểm → 409")
  void dayWithoutItems() {
    lenient().when(itemRepository.findOrderedByDayId(DAY)).thenReturn(List.of());

    ApiException e = error(16.03, 108.2, 1, "car");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(e.getMessage())
        .isEqualTo("Ngày này không có địa điểm để chỉ đường; bạn có thể hoàn thành ngày");
  }

  @Test
  @DisplayName("UTC071 Điểm tiếp theo chưa có tọa độ → 422")
  void stopWithoutCoordinates() {
    item1.getPlace().setLatitude(null);

    ApiException e = error(16.03, 108.2, 1, "car");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(e.getMessage()).isEqualTo("Địa điểm tiếp theo chưa có tọa độ hợp lệ để chỉ đường");
  }

  @Test
  @DisplayName("UTC072 GPS ở New York, cách điểm đến hơn 2.500 km → 422, không gọi Goong")
  void gpsFarFromDestination() {
    ApiException e = error(40.7, -74.0, 1, "car");

    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(e.getMessage()).contains("cách địa điểm khoảng").contains("km");
    verify(mapService, never())
        .directions(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString());
  }
}
