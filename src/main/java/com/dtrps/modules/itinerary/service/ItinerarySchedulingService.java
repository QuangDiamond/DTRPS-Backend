package com.dtrps.modules.itinerary.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.entity.PlanPlace;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceOpeningHour;
import com.dtrps.modules.poi.entity.PlacePrice;
import com.dtrps.modules.poi.repository.PlaceOpeningHourRepository;
import com.dtrps.modules.poi.repository.PlacePriceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Thuật toán quy tắc, không dùng AI: nhóm theo ngày, gần nhau, giờ mở cửa và sức chứa. */
@Service
public class ItinerarySchedulingService {
    public record Visit(Place place, LocalTime start, LocalTime end,
                        int travelMinutes, BigDecimal estimatedCost) {}

    private static final LocalTime DAY_START = LocalTime.of(9, 0);
    private static final LocalTime DAY_END = LocalTime.of(18, 0);
    private final PlaceOpeningHourRepository openingHours;
    private final PlacePriceRepository prices;

    public ItinerarySchedulingService(PlaceOpeningHourRepository openingHours,
                                      PlacePriceRepository prices) {
        this.openingHours = openingHours;
        this.prices = prices;
    }

    /** Tính hết trong bộ nhớ; nếu không xếp đủ thì KHÔNG lưu lịch trình dở dang. */
    public List<List<Visit>> arrange(TravelPlan plan, List<PlanPlace> selected) {
        long dayCount = ChronoUnit.DAYS.between(plan.getStartDate(), plan.getEndDate()) + 1;
        if (dayCount < 1 || dayCount > 60) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chuyến đi cần từ 1 đến 60 ngày");
        }
        if (selected.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Hãy chọn ít nhất một địa điểm trước khi tạo lịch trình");
        }
        if (selected.size() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mỗi kế hoạch tối đa 100 địa điểm");
        }

        List<PlanPlace> remaining = new ArrayList<>(selected);
        List<List<Visit>> result = new ArrayList<>();
        Map<Long, List<PlaceOpeningHour>> hoursCache = new HashMap<>();
        Map<Long, BigDecimal> costCache = new HashMap<>();

        int maxDaily = switch (plan.getTravelPace() == null ? "BALANCED" : plan.getTravelPace()) {
            case "RELAXED" -> 3;
            case "PACKED" -> 6;
            default -> 4;
        };
        for (int d = 0; d < dayCount; d++) {
            LocalDate date = plan.getStartDate().plusDays(d);
            LocalTime cursor = DAY_START;
            Place previous = null;
            List<Visit> visits = new ArrayList<>();
            while (!remaining.isEmpty() && visits.size() < maxDaily) {
                Visit best = null;
                PlanPlace bestRow = null;
                double bestScore = Double.POSITIVE_INFINITY;
                for (PlanPlace row : remaining) {
                    Place place = row.getPlace();
                    if (!"ACTIVE".equals(place.getStatus())) continue;
                    int travel = previous == null ? 0 : estimateTravelMinutes(previous, place);
                    LocalTime tentative = cursor.plusMinutes(travel);
                    int stay = visitDurationMinutes(place);
                    List<PlaceOpeningHour> hours = hoursCache.computeIfAbsent(place.getPlaceId(),
                            key -> openingHours.findByPlacePlaceIdOrderByDayOfWeekAsc(key));
                    LocalTime start = fitOpening(hours, date, tentative, stay);
                    if (start == null) continue;
                    LocalTime end = start.plusMinutes(stay);
                    if (end.isAfter(DAY_END)) continue;
                    double distanceScore = previous == null ? 0 : travel;
                    // Ưu tiên gần nhau; priority chỉ là yếu tố phụ.
                    double score = distanceScore - Math.min(30, Math.max(-30, row.getPriority())) * 0.1;
                    if (score < bestScore || (score == bestScore && bestRow != null
                            && row.getPlanPlaceId() < bestRow.getPlanPlaceId())) {
                        BigDecimal cost = costCache.computeIfAbsent(place.getPlaceId(),
                                key -> referenceCost(key, plan.getGroupSize()));
                        bestScore = score;
                        best = new Visit(place, start, end, travel, cost);
                        bestRow = row;
                    }
                }
                if (best == null) break;
                visits.add(best);
                cursor = best.end().plusMinutes(15); // thời gian nghỉ/chuyển hoạt động
                previous = best.place();
                remaining.remove(bestRow);
            }
            result.add(visits);
        }
        if (!remaining.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Không đủ ngày hoặc khung giờ mở cửa để xếp tất cả "
                            + remaining.size() + " địa điểm đã chọn. Hãy tăng số ngày hoặc bỏ bớt địa điểm.");
        }
        return result;
    }

    private int visitDurationMinutes(Place place) {
        String group = place.getCategory() != null && place.getCategory().getParent() != null
                ? place.getCategory().getParent().getCode().toLowerCase() : "";
        if (group.contains("fnb") || group.contains("food")) return 75;
        if (group.contains("shopping")) return 90;
        if (group.contains("lodging")) return 60;
        return 120;
    }

    /** Nếu không có dữ liệu giờ mở cửa: giả định được tham quan 09:00–18:00. */
    private LocalTime fitOpening(List<PlaceOpeningHour> hours, LocalDate date,
                                 LocalTime tentative, int stayMinutes) {
        if (hours.isEmpty()) return tentative;
        short weekDay = (short) date.getDayOfWeek().getValue();
        PlaceOpeningHour hour = hours.stream().filter(h -> h.getDayOfWeek() == weekDay)
                .findFirst().orElse(null);
        if (hour == null || Boolean.TRUE.equals(hour.getIsClosed())) return null;
        LocalTime from = hour.getOpenTime() == null ? DAY_START : hour.getOpenTime();
        LocalTime to = hour.getCloseTime() == null ? DAY_END : hour.getCloseTime();
        LocalTime start = tentative.isBefore(from) ? from : tentative;
        return start.plusMinutes(stayMinutes).isAfter(to) ? null : start;
    }

    /** Khoảng cách đường chim bay và tốc độ giả định 25 km/h: chỉ là ước lượng. */
    private int estimateTravelMinutes(Place a, Place b) {
        if (a.getLatitude() == null || a.getLongitude() == null
                || b.getLatitude() == null || b.getLongitude() == null) return 25;
        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double latDelta = lat2 - lat1;
        double lngDelta = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.pow(Math.sin(latDelta / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(lngDelta / 2), 2);
        double km = 6371 * 2 * Math.asin(Math.min(1, Math.sqrt(h)));
        return Math.min(120, Math.max(10, (int) Math.ceil(km / 25 * 60) + 10));
    }

    /** Giá chỉ là tham khảo từ place_prices, không phải giá đặt dịch vụ thực tế. */
    private BigDecimal referenceCost(Long placeId, Integer groupSize) {
        List<PlacePrice> rows = prices.findByPlacePlaceIdOrderByPriceIdAsc(placeId);
        PlacePrice cheapest = rows.stream().filter(p -> "VND".equals(p.getCurrency()))
                .min(Comparator.comparing(PlacePrice::getAmount)).orElse(null);
        if (cheapest == null) return null; // Không có bảng giá: chi phí chưa xác định
        String priceType = cheapest.getPriceType().toUpperCase();
        boolean perPerson = priceType.contains("TICKET") || priceType.contains("MEAL")
                || priceType.contains("PERSON");
        return perPerson ? cheapest.getAmount().multiply(
                BigDecimal.valueOf(groupSize == null ? 1 : groupSize)) : cheapest.getAmount();
    }
}