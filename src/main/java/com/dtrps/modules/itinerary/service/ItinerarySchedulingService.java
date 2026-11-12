
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

/**
 * Sắp xếp lịch trình bằng quy tắc (không dùng AI).
 *
 * Mục tiêu:
 * - Phân bổ tương đối đồng đều số địa điểm qua các ngày (6 điểm / 3 ngày -> 2/2/2).
 * - Tôn trọng giờ mở cửa, 09:00-18:00, thời gian tham quan và di chuyển.
 * - Ưu tiên địa điểm có ít ngày mở cửa còn lại, sau đó ưu tiên gần nhau.
 * - Nếu không thể chia đều do ràng buộc thực tế, thử cách xếp linh hoạt hơn.
 * - Chỉ trả kết quả khi đã xếp TẤT CẢ địa điểm; không lưu lịch trình dở dang.
 */
@Service
public class ItinerarySchedulingService {

    public record Visit(
            Place place,
            LocalTime start,
            LocalTime end,
            int travelMinutes,
            BigDecimal estimatedCost
    ) {}

    private record Attempt(
            List<List<Visit>> days,
            int unassignedCount
    ) {}

    private static final LocalTime DAY_START = LocalTime.of(9, 0);
    private static final LocalTime DAY_END = LocalTime.of(18, 0);
    private static final int REST_MINUTES = 15;

    private final PlaceOpeningHourRepository openingHours;
    private final PlacePriceRepository prices;

    public ItinerarySchedulingService(
            PlaceOpeningHourRepository openingHours,
            PlacePriceRepository prices
    ) {
        this.openingHours = openingHours;
        this.prices = prices;
    }

    /**
     * Sắp xếp hoàn toàn trong bộ nhớ. ItineraryService chỉ lưu sau khi hàm này thành công.
     */
    public List<List<Visit>> arrange(TravelPlan plan, List<PlanPlace> selected) {
        long requestedDays = ChronoUnit.DAYS.between(
                plan.getStartDate(), plan.getEndDate()) + 1;

        if (requestedDays < 1 || requestedDays > 60) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Chuyến đi cần từ 1 đến 60 ngày");
        }
        if (selected == null || selected.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Hãy chọn ít nhất một địa điểm trước khi tạo lịch trình");
        }
        if (selected.size() > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Mỗi kế hoạch tối đa 100 địa điểm");
        }

        int dayCount = (int) requestedDays;
        int maxDaily = switch (
                plan.getTravelPace() == null ? "BALANCED" : plan.getTravelPace()) {
            case "RELAXED" -> 3;
            case "PACKED" -> 6;
            default -> 4;
        };

        // Không thể xếp vượt mức sức chứa theo nhịp độ du lịch đã chọn.
        if (selected.size() > dayCount * maxDaily) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Có " + selected.size() + " địa điểm trong " + dayCount
                            + " ngày, nhưng nhịp độ hiện tại cho phép tối đa "
                            + maxDaily + " địa điểm/ngày. "
                            + "Hãy tăng số ngày, đổi nhịp độ hoặc giảm số địa điểm.");
        }

        // Cache dùng chung cho các lần thử để không truy vấn DB lặp lại.
        Map<Long, List<PlaceOpeningHour>> hoursCache = new HashMap<>();
        Map<Long, BigDecimal> costCache = new HashMap<>();
        List<Attempt> attempts = new ArrayList<>();

        // Lần 1: cân bằng số địa điểm, ưu tiên những nơi ít ngày mở cửa.
        attempts.add(buildAttempt(
                plan, selected, dayCount, maxDaily, true, true,
                hoursCache, costCache));

        // Lần 2: vẫn cân bằng, nhưng ưu tiên cụm địa điểm gần nhau.
        attempts.add(buildAttempt(
                plan, selected, dayCount, maxDaily, true, false,
                hoursCache, costCache));

        // Lần 3: cho phép xếp nhiều hơn vào ngày cần thiết nếu giờ mở cửa hạn chế.
        attempts.add(buildAttempt(
                plan, selected, dayCount, maxDaily, false, true,
                hoursCache, costCache));

        // Lần 4: dự phòng tương đương chiến lược cũ (ưu tiên khoảng cách).
        attempts.add(buildAttempt(
                plan, selected, dayCount, maxDaily, false, false,
                hoursCache, costCache));

        Attempt best = null;
        for (Attempt attempt : attempts) {
            if (attempt.unassignedCount() != 0) continue;
            if (best == null || scheduleScore(attempt) < scheduleScore(best)) {
                best = attempt;
            }
        }

        if (best != null) {
            return best.days();
        }

        int fewestUnassigned = attempts.stream()
                .mapToInt(Attempt::unassignedCount)
                .min()
                .orElse(selected.size());
        throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Không đủ ngày hoặc khung giờ mở cửa để xếp tất cả "
                        + selected.size() + " địa điểm đã chọn. Còn "
                        + fewestUnassigned + " địa điểm chưa xếp được. "
                        + "Hãy tăng số ngày hoặc bỏ bớt địa điểm.");
    }

    /**
     * Một lần thử phân bổ:
     * balanced=true: ngày hiện tại chỉ nhận khoảng ceil(số điểm còn lại / số ngày còn lại).
     * balanced=false: ngày hiện tại được nhận tối đa maxDaily điểm (phương án dự phòng).
     */
    private Attempt buildAttempt(
            TravelPlan plan,
            List<PlanPlace> selected,
            int dayCount,
            int maxDaily,
            boolean balanced,
            boolean preferLimitedOpeningDays,
            Map<Long, List<PlaceOpeningHour>> hoursCache,
            Map<Long, BigDecimal> costCache
    ) {
        List<PlanPlace> remaining = new ArrayList<>(selected);
        List<List<Visit>> result = new ArrayList<>(dayCount);

        for (int d = 0; d < dayCount; d++) {
            LocalDate date = plan.getStartDate().plusDays(d);
            int daysLeft = dayCount - d;
            int target = balanced
                    ? Math.min(maxDaily, (remaining.size() + daysLeft - 1) / daysLeft)
                    : maxDaily;

            LocalTime cursor = DAY_START;
            Place previous = null;
            List<Visit> visits = new ArrayList<>();

            while (!remaining.isEmpty() && visits.size() < target) {
                Visit bestVisit = null;
                PlanPlace bestRow = null;
                double bestScore = Double.POSITIVE_INFINITY;

                for (PlanPlace row : remaining) {
                    Place place = row.getPlace();
                    if (place == null || !"ACTIVE".equals(place.getStatus())) {
                        continue;
                    }

                    int travel = previous == null
                            ? 0 : estimateTravelMinutes(previous, place);
                    LocalTime tentative = cursor.plusMinutes(travel);
                    int stay = visitDurationMinutes(place);
                    List<PlaceOpeningHour> hours = hoursFor(place, hoursCache);
                    LocalTime start = fitOpening(hours, date, tentative, stay);
                    if (start == null) continue;

                    LocalTime end = start.plusMinutes(stay);
                    if (end.isAfter(DAY_END)) continue;

                    int priority = row.getPriority() == null ? 0 : row.getPriority();
                    double score = travel - Math.min(30, Math.max(-30, priority)) * 0.1;

                    if (preferLimitedOpeningDays) {
                        // Ít ngày khác có thể tham quan -> ưu tiên xếp sớm.
                        int futureDays = countAvailableFutureDays(
                                plan.getStartDate(), d + 1, dayCount,
                                hours, stay);
                        score += Math.min(3, futureDays) * 30.0;
                        // Chờ lâu tới giờ mở cửa khiến lịch trình kém hiệu quả.
                        score += ChronoUnit.MINUTES.between(tentative, start) * 0.25;
                    }

                    Long candidateId = row.getPlanPlaceId();
                    Long currentId = bestRow == null ? null : bestRow.getPlanPlaceId();
                    boolean betterTie = bestRow != null && candidateId != null
                            && (currentId == null || candidateId < currentId);

                    if (score < bestScore || (score == bestScore && betterTie)) {
                        BigDecimal cost = costCache.computeIfAbsent(
                                place.getPlaceId(),
                                key -> referenceCost(key, plan.getGroupSize()));
                        bestScore = score;
                        bestVisit = new Visit(place, start, end, travel, cost);
                        bestRow = row;
                    }
                }

                if (bestVisit == null) break;

                visits.add(bestVisit);
                cursor = bestVisit.end().plusMinutes(REST_MINUTES);
                previous = bestVisit.place();
                remaining.remove(bestRow);
            }
            result.add(visits);
        }
        return new Attempt(result, remaining.size());
    }

    /**
     * So sánh các lịch trình hợp lệ:
     * tổng bình phương số hoạt động/ngày càng nhỏ thì càng cân bằng;
     * nếu cân bằng ngang nhau, chọn lịch trình ít thời gian di chuyển hơn.
     */
    private long scheduleScore(Attempt attempt) {
        long score = 0;
        for (List<Visit> day : attempt.days()) {
            score += (long) day.size() * day.size() * 10_000;
            for (Visit visit : day) {
                score += visit.travelMinutes();
            }
        }
        return score;
    }

    private int countAvailableFutureDays(
            LocalDate firstDate,
            int startIndex,
            int dayCount,
            List<PlaceOpeningHour> hours,
            int stay
    ) {
        int count = 0;
        for (int d = startIndex; d < dayCount; d++) {
            LocalTime start = fitOpening(
                    hours, firstDate.plusDays(d), DAY_START, stay);
            if (start != null && !start.plusMinutes(stay).isAfter(DAY_END)) {
                count++;
            }
        }
        return count;
    }

    private List<PlaceOpeningHour> hoursFor(
            Place place,
            Map<Long, List<PlaceOpeningHour>> cache
    ) {
        return cache.computeIfAbsent(
                place.getPlaceId(),
                key -> openingHours.findByPlacePlaceIdOrderByDayOfWeekAsc(key));
    }

    private int visitDurationMinutes(Place place) {
        String group = place.getCategory() != null
                && place.getCategory().getParent() != null
                ? place.getCategory().getParent().getCode().toLowerCase() : "";
        if (group.contains("fnb") || group.contains("food")) return 75;
        if (group.contains("shopping")) return 90;
        if (group.contains("lodging")) return 60;
        return 120;
    }

    /** Nếu không có dữ liệu giờ mở cửa, giả định tham quan được 09:00-18:00. */
    private LocalTime fitOpening(
            List<PlaceOpeningHour> hours,
            LocalDate date,
            LocalTime tentative,
            int stayMinutes
    ) {
        if (hours.isEmpty()) return tentative;

        short weekDay = (short) date.getDayOfWeek().getValue();
        PlaceOpeningHour hour = hours.stream()
                .filter(h -> h.getDayOfWeek() == weekDay)
                .findFirst().orElse(null);
        if (hour == null || Boolean.TRUE.equals(hour.getIsClosed())) return null;

        LocalTime from = hour.getOpenTime() == null ? DAY_START : hour.getOpenTime();
        LocalTime to = hour.getCloseTime() == null ? DAY_END : hour.getCloseTime();
        LocalTime start = tentative.isBefore(from) ? from : tentative;
        return start.plusMinutes(stayMinutes).isAfter(to) ? null : start;
    }

    /** Khoảng cách đường chim bay, tốc độ giả định 25 km/h (chỉ là ước lượng). */
    private int estimateTravelMinutes(Place a, Place b) {
        if (a.getLatitude() == null || a.getLongitude() == null
                || b.getLatitude() == null || b.getLongitude() == null) return 25;

        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double latDelta = lat2 - lat1;
        double lngDelta = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.pow(Math.sin(latDelta / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.pow(Math.sin(lngDelta / 2), 2);
        double km = 6371 * 2 * Math.asin(Math.min(1, Math.sqrt(h)));
        return Math.min(120, Math.max(10, (int) Math.ceil(km / 25 * 60) + 10));
    }

    /** Giá tham khảo từ place_prices, không phải giá đặt dịch vụ thực tế. */
    private BigDecimal referenceCost(Long placeId, Integer groupSize) {
        List<PlacePrice> rows = prices.findByPlacePlaceIdOrderByPriceIdAsc(placeId);
        PlacePrice cheapest = rows.stream()
                .filter(p -> "VND".equals(p.getCurrency()))
                .min(Comparator.comparing(PlacePrice::getAmount))
                .orElse(null);
        if (cheapest == null) return null;

        String priceType = cheapest.getPriceType().toUpperCase();
        boolean perPerson = priceType.contains("TICKET")
                || priceType.contains("MEAL")
                || priceType.contains("PERSON");
        return perPerson
                ? cheapest.getAmount().multiply(
                BigDecimal.valueOf(groupSize == null ? 1 : groupSize))
                : cheapest.getAmount();
    }
}
