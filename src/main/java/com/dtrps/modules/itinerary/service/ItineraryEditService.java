package com.dtrps.modules.itinerary.service;


import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.entity.*;
import com.dtrps.modules.itinerary.repository.*;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceOpeningHour;
import com.dtrps.modules.poi.entity.PlacePrice;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.poi.repository.PlaceOpeningHourRepository;
import com.dtrps.modules.poi.repository.PlacePriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Chỉnh sửa thủ công trên lịch trình ACTIVE; không gọi AI hay tạo lại phiên bản. */
@Service
public class ItineraryEditService {
    private final TravelPlanRepository plans;
    private final ItineraryRepository itineraries;
    private final ItineraryDayRepository days;
    private final ItineraryItemRepository items;
    private final TripProgressRepository progress;
    private final PlanPlaceRepository selected;
    private final PlaceRepository places;
    private final PlaceOpeningHourRepository hours;
    private final PlacePriceRepository prices;

    public ItineraryEditService(TravelPlanRepository plans, ItineraryRepository itineraries,
                                ItineraryDayRepository days, ItineraryItemRepository items,
                                TripProgressRepository progress, PlanPlaceRepository selected,
                                PlaceRepository places, PlaceOpeningHourRepository hours, PlacePriceRepository prices) {
        this.plans = plans;
        this.itineraries = itineraries;
        this.days = days;
        this.items = items;
        this.progress = progress;
        this.selected = selected;
        this.places = places;
        this.hours = hours;
        this.prices = prices;
    }

    public record EditRequest(Long itineraryId, Long itemId, Long placeId,
                              LocalTime startTime, LocalTime endTime, Long swapWithItemId) {}

    @Transactional
    public void edit(Long userId, Long planId, EditRequest request) {
        if (request == null || request.itineraryId() == null || request.itemId() == null) {
            throw invalid("Thiếu mã lịch trình hoặc hoạt động.");
        }
        // Cùng thứ tự khóa với TripProgressService và ItineraryItemService.
        TravelPlan plan = plans.lockById(planId).orElseThrow(this::notFound);
        if (!Objects.equals(plan.getUser().getUserId(), userId)) throw notFound();
        if (!TravelPlanStatus.isEditable(plan.getStatus()) ||
                progress.findByItineraryPlanPlanIdOrderByProgressIdAsc(planId).stream()
                        .anyMatch(p -> p.getStartedAt() != null || p.getCompletedAt() != null
                                || !"NOT_STARTED".equals(p.getStatus()))) {
            throw conflict("Chỉ được sửa lịch trình trước khi chuyến đi bắt đầu.");
        }
        Itinerary active = itineraries.findFirstByPlanPlanIdAndStatusOrderByVersionNumberDesc(planId, "ACTIVE")
                .orElseThrow(this::notFound);
        if (!Objects.equals(active.getItineraryId(), request.itineraryId())) {
            throw conflict("Lịch trình đã đổi phiên bản. Vui lòng tải lại.");
        }
        List<ItineraryDay> itineraryDays = days.findByItineraryItineraryIdOrderByDayNumberAsc(active.getItineraryId());
        Map<Long, List<ItineraryItem>> byDay = new LinkedHashMap<>();
        Map<Long, ItineraryItem> byId = new HashMap<>();
        // Khóa các ngày theo thứ tự nhất quán sau khi đã khóa plan.
        for (ItineraryDay day : itineraryDays) {
            days.lockByDayId(day.getDayId()).orElseThrow(this::notFound);
            List<ItineraryItem> rows = items.findOrderedByDayId(day.getDayId());
            byDay.put(day.getDayId(), rows);
            for (ItineraryItem row : rows) byId.put(row.getItemId(), row);
        }
        ItineraryItem first = byId.get(request.itemId());
        if (first == null) throw notFound();
        requirePlanned(first);
        Map<Long, String> previousLegs = new HashMap<>();
        for (List<ItineraryItem> rows : byDay.values()) {
            ItineraryItem previous = null;
            for (ItineraryItem row : rows) {
                previousLegs.put(row.getItemId(), legKey(previous, row));
                previous = row;
            }
        }
        Set<Long> changedDays = new LinkedHashSet<>();
        changedDays.add(first.getDay().getDayId());

        if (request.swapWithItemId() != null) {
            if (request.placeId() != null || request.startTime() != null || request.endTime() != null) {
                throw invalid("Yêu cầu hoán đổi không được kèm thay đổi giờ hoặc địa điểm.");
            }
            ItineraryItem second = byId.get(request.swapWithItemId());
            if (second == null) throw notFound();
            if (Objects.equals(first.getItemId(), second.getItemId())) throw invalid("Hãy chọn hoạt động khác.");
            requirePlanned(second);
            requireDestination(first.getPlace(), plan);
            requireDestination(second.getPlace(), plan);
            // Hoán đổi địa điểm, chi phí và ghi chú, giữ khung giờ/vị trí của hai slot.
            Place originalPlace = first.getPlace();
            BigDecimal originalCost = first.getEstimatedCost();
            String originalNotes = first.getNotes();
            first.setPlace(second.getPlace());
            first.setEstimatedCost(second.getEstimatedCost());
            first.setNotes(second.getNotes());
            second.setPlace(originalPlace);
            second.setEstimatedCost(originalCost);
            second.setNotes(originalNotes);
            changedDays.add(second.getDay().getDayId());
        } else {
            if (request.startTime() == null || request.endTime() == null || !request.endTime().isAfter(request.startTime())) {
                throw invalid("Hãy nhập đầy đủ giờ; giờ kết thúc phải sau giờ bắt đầu.");
            }
            first.setStartTime(request.startTime());
            first.setEndTime(request.endTime());
            Long oldPlaceId = first.getPlace().getPlaceId();
            if (request.placeId() != null && !Objects.equals(oldPlaceId, request.placeId())) {
                Place replacement = places.findById(request.placeId()).orElseThrow(this::notFound);
                requireDestination(replacement, plan);
                if (byId.values().stream().anyMatch(i -> Objects.equals(i.getPlace().getPlaceId(), replacement.getPlaceId()))) {
                    throw conflict("Địa điểm này đã có trong lịch trình. Hãy dùng chức năng hoán đổi.");
                }
                first.setPlace(replacement);
                first.setEstimatedCost(referenceCost(replacement, plan));
                first.setNotes(null); // Ghi chú cũ có thể mô tả địa điểm đã thay thế.
                // Đồng bộ danh sách địa điểm được chọn, giữ các lựa chọn khác nguyên vẹn.
                if (byId.values().stream().noneMatch(i -> Objects.equals(i.getPlace().getPlaceId(), oldPlaceId))) {
                    selected.findByPlanPlanIdAndPlacePlaceId(planId, oldPlaceId).ifPresent(selected::delete);
                }
                if (!selected.existsByPlanPlanIdAndPlacePlaceId(planId, replacement.getPlaceId())) {
                    PlanPlace row = new PlanPlace();
                    row.setPlan(plan);
                    row.setPlace(replacement);
                    row.setPriority(0);
                    row.setSource("USER");
                    selected.save(row);
                }
            }
        }
        // Mọi thay đổi (kể cả plan_places) rollback nếu một ngày không hợp lệ.
        for (ItineraryDay day : itineraryDays) {
            if (!changedDays.contains(day.getDayId())) continue;
            validateDay(plan, day, byDay.get(day.getDayId()), previousLegs);
        }
        items.flush();
    }

    private void requirePlanned(ItineraryItem item) {
        if (!"PLANNED".equals(item.getStatus()) || items.countActivityProgressForItem(item.getItemId()) > 0) {
            throw conflict("Hoạt động đã có tiến độ hoặc không ở trạng thái PLANNED.");
        }
    }

    private void requireDestination(Place place, TravelPlan plan) {
        if (!"ACTIVE".equals(place.getStatus())) throw invalid("Địa điểm chưa hoạt động.");
        String destination = plan.getDestination().trim();
        if (place.getProvince() == null || (!destination.equalsIgnoreCase(place.getProvince().getName())
                && !destination.equalsIgnoreCase(place.getProvince().getCode()))) {
            throw invalid("Địa điểm phải thuộc tỉnh/thành của chuyến đi.");
        }
    }

    private void validateDay(TravelPlan plan, ItineraryDay day, List<ItineraryItem> rows, Map<Long, String> previousLegs) {
        LocalDate date = plan.getStartDate().plusDays(day.getDayNumber() - 1L);
        ItineraryItem previous = null;
        for (ItineraryItem item : rows) {
            LocalTime start = item.getStartTime(), end = item.getEndTime();
            String label = "Ngày " + day.getDayNumber() + " – " + item.getPlace().getName() + ": ";
            if (start == null || end == null || !end.isAfter(start)) {
                throw invalid(label + "giờ kết thúc phải sau giờ bắt đầu.");
            }
            List<PlaceOpeningHour> schedule = hours.findByPlacePlaceIdOrderByDayOfWeekAsc(item.getPlace().getPlaceId());
            if (!schedule.isEmpty()) {
                PlaceOpeningHour opening = schedule.stream()
                        .filter(h -> h.getDayOfWeek() != null && h.getDayOfWeek() == date.getDayOfWeek().getValue())
                        .findFirst().orElse(null);
                if (opening == null || Boolean.TRUE.equals(opening.getIsClosed())
                        || (opening.getOpenTime() != null && start.isBefore(opening.getOpenTime()))
                        || (opening.getCloseTime() != null && end.isAfter(opening.getCloseTime()))) {
                    throw invalid(label + "khung giờ không phù hợp giờ mở cửa đã lưu.");
                }
            }
            int travel = previous == null ? 0 : estimateTravel(previous.getPlace(), item.getPlace());
            if (previous != null && ChronoUnit.MINUTES.between(previous.getEndTime(), start) < travel) {
                throw invalid(label + "trùng giờ hoặc không đủ " + travel + " phút di chuyển từ hoạt động trước. Hãy điều chỉnh giờ.");
            }
            item.setTravelTimeMinutes(travel);
            // Không giữ phương tiện do hệ thống/khách đã chọn nếu tuyến mới chưa được xác nhận.
            if (!Objects.equals(previousLegs.get(item.getItemId()), legKey(previous, item))) item.setTransportMode(null);
            previous = item;
        }
    }

    private String legKey(ItineraryItem previous, ItineraryItem current) {
        return (previous == null ? "START" : previous.getPlace().getPlaceId().toString()) + ":" + current.getPlace().getPlaceId();
    }

    // Cùng công thức ước lượng của bộ lập lịch hiện có, không phải Goong Directions thực tế.
    private int estimateTravel(Place a, Place b) {
        if (a.getLatitude() == null || a.getLongitude() == null || b.getLatitude() == null || b.getLongitude() == null) return 25;
        double lat1 = Math.toRadians(a.getLatitude()), lat2 = Math.toRadians(b.getLatitude());
        double h = Math.pow(Math.sin((lat2 - lat1) / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(Math.toRadians(b.getLongitude() - a.getLongitude()) / 2), 2);
        double km = 6371 * 2 * Math.asin(Math.min(1, Math.sqrt(h)));
        return Math.min(120, Math.max(10, (int) Math.ceil(km / 25 * 60) + 10));
    }

    private BigDecimal referenceCost(Place place, TravelPlan plan) {
        PlacePrice fee = prices.findByPlacePlaceIdOrderByPriceIdAsc(place.getPlaceId()).stream()
                .filter(p -> Objects.equals(plan.getCurrency(), p.getCurrency()) && p.getAmount() != null)
                .min(Comparator.comparing(PlacePrice::getAmount)).orElse(null);
        if (fee == null) return null;
        String type = Objects.toString(fee.getPriceType(), "").toUpperCase(Locale.ROOT);
        return type.contains("TICKET") || type.contains("MEAL") || type.contains("PERSON")
                ? fee.getAmount().multiply(BigDecimal.valueOf(plan.getGroupSize() == null ? 1 : plan.getGroupSize()))
                : fee.getAmount();
    }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch trình hoặc hoạt động."); }
    private ApiException invalid(String msg) { return new ApiException(HttpStatus.BAD_REQUEST, msg); }
    private ApiException conflict(String msg) { return new ApiException(HttpStatus.CONFLICT, msg); }
}
