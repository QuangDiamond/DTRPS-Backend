
package com.dtrps.modules.itinerary.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.TripNavigationDtos;
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
import com.dtrps.modules.maps.service.MapService;
import com.dtrps.modules.poi.entity.Place;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chỉ đường từ GPS hiện tại đến địa điểm PENDING tiếp theo trong ngày.
 *
 * GPS được Frontend lấy sau khi Traveller cấp quyền.
 * Backend không tự đọc vị trí thiết bị và không lưu GPS.
 *
 * Service này chỉ đọc tiến độ và gọi MapService.
 * Việc bắt đầu ngày, hoàn thành hoạt động và hoàn thành ngày
 * thuộc TripProgressService.
 */
@Service
public class TripNavigationService {

    private static final String ACTIVE = "ACTIVE";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String PENDING = "PENDING";

    private static final Set<String> VEHICLES =
            Set.of("car", "bike", "taxi", "truck", "hd");

    private final TravelPlanRepository planRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository dayRepository;
    private final ItineraryItemRepository itemRepository;
    private final TripProgressRepository tripProgressRepository;
    private final TripDayProgressRepository dayProgressRepository;
    private final ActivityProgressRepository activityProgressRepository;
    private final MapService mapService;

    public TripNavigationService(
            TravelPlanRepository planRepository,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository dayRepository,
            ItineraryItemRepository itemRepository,
            TripProgressRepository tripProgressRepository,
            TripDayProgressRepository dayProgressRepository,
            ActivityProgressRepository activityProgressRepository,
            MapService mapService) {

        this.planRepository = planRepository;
        this.itineraryRepository = itineraryRepository;
        this.dayRepository = dayRepository;
        this.itemRepository = itemRepository;
        this.tripProgressRepository = tripProgressRepository;
        this.dayProgressRepository = dayProgressRepository;
        this.activityProgressRepository = activityProgressRepository;
        this.mapService = mapService;
    }

    /**
     * Lấy tuyến từ vị trí GPS hiện tại đến hoạt động
     * chưa hoàn thành đầu tiên của ngày đang diễn ra.
     *
     * Frontend gọi lại với GPS mới khi cần tính lại đường.
     */
    @Transactional(readOnly = true)
    public TripNavigationDtos.NextRouteResponse getNextRoute(
            Long userId,
            Long planId,
            Integer dayNumber,
            double fromLat,
            double fromLng,
            String vehicle) {

        validateCoordinates(fromLat, fromLng, "GPS hiện tại");
        String normalizedVehicle = normalizeVehicle(vehicle);

        // 1. Kiểm tra chủ sở hữu Travel Plan.
        TravelPlan plan = planRepository
                .findByPlanIdAndUserUserId(planId, userId)
                .orElseThrow(() ->
                        notFound("Không tìm thấy kế hoạch du lịch"));

        if (!IN_PROGRESS.equals(plan.getStatus())) {
            throw conflict(
                    "Chuyến đi chưa bắt đầu hoặc không còn đang diễn ra");
        }

        // 2. Lấy lịch trình ACTIVE.
        Itinerary itinerary = itineraryRepository
                .findFirstByPlanPlanIdAndStatusOrderByVersionNumberDesc(
                        planId, ACTIVE)
                .orElseThrow(() ->
                        notFound("Kế hoạch chưa có lịch trình ACTIVE"));

        // 3. Xác định ngày đang xem.
        ItineraryDay day = findDay(
                itinerary.getItineraryId(), dayNumber);

        // 4. Kiểm tra tiến độ toàn chuyến.
        TripProgress progress = tripProgressRepository
                .findByItineraryItineraryId(itinerary.getItineraryId())
                .orElseThrow(() ->
                        conflict("Bạn chưa bắt đầu chuyến đi"));

        if (!IN_PROGRESS.equals(progress.getStatus())) {
            throw conflict(
                    "Chuyến đi không ở trạng thái đang diễn ra");
        }

        // 5. Ngày này phải được bắt đầu.
        TripDayProgress dayProgress = dayProgressRepository
                .findByProgressProgressIdAndDayDayId(
                        progress.getProgressId(), day.getDayId())
                .orElseThrow(() ->
                        conflict("Bạn chưa bắt đầu ngày này"));

        if (!IN_PROGRESS.equals(dayProgress.getStatus())) {
            throw conflict(
                    "Ngày này không ở trạng thái đang diễn ra");
        }

        // 6. Lấy các địa điểm theo itemOrder.
        List<ItineraryItem> items =
                itemRepository.findOrderedByDayId(day.getDayId());

        if (items.isEmpty()) {
            throw conflict(
                    "Ngày này không có địa điểm để chỉ đường; "
                            + "bạn có thể hoàn thành ngày");
        }

        // 7. Lấy trạng thái thực tế từng địa điểm.
        // Hoạt động chưa có ActivityProgress được coi là PENDING.
        Map<Long, String> statuses = activityProgressRepository
                .findOrderedByProgressIdAndDayId(
                        progress.getProgressId(), day.getDayId())
                .stream()
                .collect(Collectors.toMap(
                        ap -> ap.getItem().getItemId(),
                        ActivityProgress::getStatus,
                        (first, second) -> second));

        // 8. Tìm địa điểm PENDING đầu tiên.
        ItineraryItem nextItem = items.stream()
                .filter(item ->
                        PENDING.equals(
                                statuses.getOrDefault(
                                        item.getItemId(), PENDING)))
                .findFirst()
                .orElseThrow(() ->
                        conflict(
                                "Đã xử lý tất cả địa điểm; "
                                        + "hãy hoàn thành ngày tham quan"));

        // 9. Lấy tọa độ điểm đến.
        Place destination = nextItem.getPlace();

        Double toLat = destination.getLatitude();
        Double toLng = destination.getLongitude();

        if (toLat == null
                || toLng == null
                || !isValidCoordinates(toLat, toLng)) {

            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Địa điểm tiếp theo chưa có tọa độ hợp lệ để chỉ đường");
        }

        // 10. Gọi Goong Directions thông qua MapService.
        RouteResponse route = mapService.directions(
                fromLat,
                fromLng,
                toLat,
                toLng,
                normalizedVehicle);

        // 11. Trả điểm đến và tuyến đường cho Frontend.
        return new TripNavigationDtos.NextRouteResponse(
                plan.getPlanId(),
                itinerary.getItineraryId(),
                day.getDayId(),
                day.getDayNumber(),
                nextItem.getItemId(),
                nextItem.getItemOrder(),
                destination.getPlaceId(),
                destination.getName(),
                destination.getAddress(),
                toLat,
                toLng,
                normalizedVehicle,
                route);
    }

    /**
     * Tìm ngày thuộc lịch trình theo dayNumber.
     */
    private ItineraryDay findDay(
            Long itineraryId,
            Integer dayNumber) {

        if (dayNumber == null || dayNumber < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Số ngày không hợp lệ");
        }

        return dayRepository
                .findByItineraryItineraryIdOrderByDayNumberAsc(
                        itineraryId)
                .stream()
                .filter(day ->
                        Objects.equals(
                                day.getDayNumber(), dayNumber))
                .findFirst()
                .orElseThrow(() ->
                        notFound("Không tìm thấy ngày trong lịch trình"));
    }

    /**
     * Kiểm tra phương tiện theo các giá trị Goong hỗ trợ.
     */
    private static String normalizeVehicle(String vehicle) {

        String value = vehicle == null || vehicle.isBlank()
                ? "car"
                : vehicle.trim().toLowerCase(Locale.ROOT);

        if (!VEHICLES.contains(value)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Phương tiện phải là car, bike, taxi, truck hoặc hd");
        }

        return value;
    }

    /**
     * Kiểm tra GPS từ Frontend.
     */
    private static void validateCoordinates(
            double latitude,
            double longitude,
            String label) {

        if (!isValidCoordinates(latitude, longitude)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    label + " không có tọa độ hợp lệ");
        }
    }

    private static boolean isValidCoordinates(
            double latitude,
            double longitude) {

        return Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90.0
                && latitude <= 90.0
                && longitude >= -180.0
                && longitude <= 180.0;
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
