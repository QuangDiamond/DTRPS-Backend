
package com.dtrps.modules.itinerary.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.TravelPlanDtos;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.entity.PlanPlace;
import com.dtrps.modules.itinerary.repository.ItineraryRepository;
import com.dtrps.modules.itinerary.repository.ItinerarySummaryRepository;
import com.dtrps.modules.itinerary.repository.PlanInterestRepository;
import com.dtrps.modules.itinerary.repository.PlanPlaceRepository;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.itinerary.repository.TripDayProgressRepository;
import com.dtrps.modules.itinerary.repository.TripProgressRepository;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.PlacePrice;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.poi.repository.PlacePriceRepository;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import com.dtrps.modules.weather.service.WeatherNotificationService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
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
 * Tạo, xem, sửa, xóa và đổi trạng thái kế hoạch du lịch.
 *
 * Chỉ chủ sở hữu được thao tác.
 * Trạng thái thực tế của chuyến đi được quản lý
 * thông qua TripProgressService.
 */
@Service
public class TravelPlanService {

    static final String PLAN_NOT_FOUND =
            "Không tìm thấy kế hoạch du lịch";

    static final String INVALID_DATES =
            "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu";

    static final String NOT_EDITABLE =
            "Chỉ sửa được kế hoạch ở trạng thái Nháp hoặc Đã lên kế hoạch";

    static final String ITINERARY_LOCKS_TRIP =
            "Kế hoạch đã có lịch trình nên không thể đổi điểm đến, ngày đi hoặc ngày về";

    static final String NOT_DELETABLE =
            "Không thể xóa kế hoạch khi chuyến đi đang diễn ra";

    private static final int MAX_CODES = 50;

    private static final Set<String> EXPERIENCE_CODES = Set.of(
            "PURPOSE_RELAX", "PURPOSE_ADVENTURE", "PURPOSE_BOND", "PURPOSE_FUN", "PURPOSE_CULTURE", "PURPOSE_ROMANCE",
            "STYLE_QUIET", "STYLE_ACTIVE", "STYLE_FREE", "STYLE_PLANNED", "STYLE_COMFORT", "STYLE_LOCAL",
            "EXPERIENCE_NATURE", "EXPERIENCE_FOOD", "EXPERIENCE_PHOTO", "EXPERIENCE_OUTDOOR", "EXPERIENCE_ART_CULTURE", "EXPERIENCE_WELLNESS",
            "MEMORY_PHOTOS", "MEMORY_STORIES", "MEMORY_UNFORGETTABLE", "MEMORY_TOGETHER"
    );

    private final TravelPlanRepository planRepository;
    private final PlanInterestRepository planInterestRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItinerarySummaryRepository itinerarySummaryRepository;
    private final PlanPlaceRepository planPlaceRepository;
    private final PlacePriceRepository placePriceRepository;
    private final TripDayProgressRepository tripDayProgressRepository;
    private final TripProgressRepository tripProgressRepository;
    private final UserRepository userRepository;
    private final PlaceCategoryRepository categoryRepository;
    private final WeatherNotificationService weatherNotificationService;

    public TravelPlanService(
            TravelPlanRepository planRepository,
            PlanInterestRepository planInterestRepository,
            ItineraryRepository itineraryRepository,
            ItinerarySummaryRepository itinerarySummaryRepository,
            PlanPlaceRepository planPlaceRepository,
            PlacePriceRepository placePriceRepository,
            TripDayProgressRepository tripDayProgressRepository,
            TripProgressRepository tripProgressRepository,
            UserRepository userRepository,
            PlaceCategoryRepository categoryRepository,
            WeatherNotificationService weatherNotificationService) {

        this.planRepository = planRepository;
        this.planInterestRepository = planInterestRepository;
        this.itineraryRepository = itineraryRepository;
        this.itinerarySummaryRepository = itinerarySummaryRepository;
        this.planPlaceRepository = planPlaceRepository;
        this.placePriceRepository = placePriceRepository;
        this.tripDayProgressRepository = tripDayProgressRepository;
        this.tripProgressRepository = tripProgressRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.weatherNotificationService = weatherNotificationService;
    }

    // =========================================================
    // TẠO KẾ HOẠCH
    // =========================================================

    @Transactional
    public TravelPlanDtos.PlanResponse create(
            Long userId,
            TravelPlanDtos.CreateRequest request) {

        User user = userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.NOT_FOUND,
                                "Không tìm thấy người dùng"));

        TravelPlan plan = new TravelPlan();

        plan.setUser(user);
        plan.setStatus(TravelPlanStatus.DRAFT);

        applyFields(plan, request);

        return toResponse(planRepository.saveAndFlush(plan));
    }

    // =========================================================
    // XEM KẾ HOẠCH
    // =========================================================

    @Transactional(readOnly = true)
    public List<TravelPlanDtos.PlanResponse> getMyPlans(
            Long userId) {

        return planRepository
                .findByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TravelPlanDtos.PlanResponse getById(
            Long userId,
            Long planId) {

        return toResponse(findOwnedPlan(userId, planId));
    }

    // =========================================================
    // SỬA KẾ HOẠCH
    // =========================================================

    @Transactional
    public TravelPlanDtos.PlanResponse update(
            Long userId,
            Long planId,
            TravelPlanDtos.UpdateRequest request) {

        // Khóa TravelPlan để đồng bộ với startDay().
        TravelPlan plan = lockOwnedPlan(userId, planId);

        if (!TravelPlanStatus.isEditable(plan.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    NOT_EDITABLE);
        }

        // Không cho sửa nếu chuyến đi thực tế đã bắt đầu.
        if (hasStartedTrip(planId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Chuyến đi đã bắt đầu, không thể sửa kế hoạch");
        }

        // Nếu đã có itinerary thì không được thay đổi
        // điểm đến hoặc ngày đi/ngày về.
        boolean tripChanged =
                !Objects.equals(
                        plan.getDestination(),
                        trimmed(request.destination()))
                        || !Objects.equals(
                        plan.getStartDate(),
                        request.startDate())
                        || !Objects.equals(
                        plan.getEndDate(),
                        request.endDate());

        if (tripChanged
                && itineraryRepository.existsByPlanPlanId(planId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ITINERARY_LOCKS_TRIP);
        }

        applyFields(plan, request);

        return toResponse(planRepository.saveAndFlush(plan));
    }

    // =========================================================
    // XÓA KẾ HOẠCH
    // =========================================================

    @Transactional
    public void delete(Long userId, Long planId) {

        TravelPlan plan = lockOwnedPlan(userId, planId);

        if (!TravelPlanStatus.isDeletable(plan.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    NOT_DELETABLE);
        }

        // Phòng trường hợp trạng thái TravelPlan
        // chưa đồng bộ với TripProgress.
        if (hasRunningTrip(planId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    NOT_DELETABLE);
        }

        // Xóa từ bảng con đến bảng cha.
        planRepository.deleteActivityProgressByPlanId(planId);

        // Xóa tiến độ từng ngày trước TripProgress
        // và ItineraryDay để không vi phạm khóa ngoại.
        tripDayProgressRepository.deleteByPlanId(planId);

        planRepository.deleteTripProgressByPlanId(planId);
        planRepository.deleteItinerarySharesByPlanId(planId);
        planRepository.deleteItineraryItemsByPlanId(planId);
        planRepository.deleteItineraryDaysByPlanId(planId);
        planRepository.deleteItinerariesByPlanId(planId);
        planRepository.deletePlanPlacesByPlanId(planId);
        planInterestRepository.deleteByPlanId(planId);
        planRepository.deleteTripFeedbackByPlanId(planId);
        planRepository.deletePlanById(planId);
    }

    // =========================================================
    // ĐỔI TRẠNG THÁI KẾ HOẠCH
    // =========================================================

    /**
     * Chỉ cho phép đổi trạng thái thủ công
     * khi chuyến đi chưa bắt đầu.
     *
     * IN_PROGRESS: do TripProgressService.startDay()
     * COMPLETED: do TripProgressService.completeDay()
     */
    @Transactional
    public TravelPlanDtos.PlanResponse changeStatus(
            Long userId,
            Long planId,
            String newStatus) {

        TravelPlan plan = lockOwnedPlan(userId, planId);

        String current = plan.getStatus();

        if (!TravelPlanStatus.isKnown(newStatus)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Trạng thái không hợp lệ");
        }

        if (newStatus.equals(current)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Kế hoạch đã ở trạng thái " + label(current));
        }

        // Giữ nguyên quy tắc chuyển trạng thái gốc.
        if (!TravelPlanStatus.canTransition(current, newStatus)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Không thể chuyển kế hoạch từ "
                            + label(current)
                            + " sang "
                            + label(newStatus)
                            + transitionHint(current));
        }

        // Không chuyển thủ công sang IN_PROGRESS.
        if (TravelPlanStatus.IN_PROGRESS.equals(newStatus)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Hãy dùng chức năng Bắt đầu chuyến đi của ngày "
                            + "để chuyển sang Đang diễn ra");
        }

        // Không chuyển thủ công sang COMPLETED.
        if (TravelPlanStatus.COMPLETED.equals(newStatus)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Hãy hoàn thành các hoạt động và ngày tham quan "
                            + "để kết thúc chuyến đi");
        }

        // Chưa có nghiệp vụ hủy chuyến đang chạy
        // đồng bộ cả TripProgress và TripDayProgress.
        if (TravelPlanStatus.IN_PROGRESS.equals(current)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Chuyến đi đang diễn ra, không thể hủy qua "
                            + "API đổi trạng thái kế hoạch");
        }

        // Kiểm tra cả tiến độ của các itinerary ARCHIVED.
        if (hasStartedTrip(planId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Chuyến đi đã bắt đầu, không thể đổi trạng thái "
                            + "kế hoạch thủ công");
        }

        plan.setStatus(newStatus);

        TravelPlanDtos.PlanResponse response = toResponse(planRepository.saveAndFlush(plan));

        // Kế hoạch vừa được xác nhận (PLANNED) — nếu đã qua 18h thì báo ngay thời tiết xấu của
        // ngày mai cho hoạt động ngoài trời, không đợi job 18h hôm sau (sẽ bị lệch mất ngày).
        if (TravelPlanStatus.PLANNED.equals(newStatus)) {
            weatherNotificationService.checkPlanTomorrowIfLate(planId);
        }

        return response;
    }

    // =========================================================
    // HÀM PHỤ — QUYỀN SỞ HỮU
    // =========================================================

    private TravelPlan findOwnedPlan(
            Long userId,
            Long planId) {

        return planRepository
                .findByPlanIdAndUserUserId(planId, userId)
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.NOT_FOUND,
                                PLAN_NOT_FOUND));
    }

    /**
     * Khóa TravelPlan trước khi cập nhật,
     * đồng nhất với TripProgressService.
     */
    private TravelPlan lockOwnedPlan(
            Long userId,
            Long planId) {

        TravelPlan plan = planRepository
                .lockById(planId)
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.NOT_FOUND,
                                PLAN_NOT_FOUND));

        if (!Objects.equals(
                plan.getUser().getUserId(), userId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    PLAN_NOT_FOUND);
        }

        return plan;
    }

    // =========================================================
    // HÀM PHỤ — TIẾN ĐỘ CHUYẾN ĐI
    // =========================================================

    /**
     * Kiểm tra tất cả phiên bản lịch trình,
     * kể cả phiên bản ARCHIVED.
     */
    private boolean hasStartedTrip(Long planId) {

        return tripProgressRepository
                .findByItineraryPlanPlanIdOrderByProgressIdAsc(planId)
                .stream()
                .anyMatch(progress ->
                        progress.getStartedAt() != null
                                || progress.getCompletedAt() != null
                                || !"NOT_STARTED".equals(progress.getStatus()));
    }

    /**
     * Kiểm tra có phiên bản chuyến đi nào
     * đang thực hiện hay không.
     */
    private boolean hasRunningTrip(Long planId) {

        return tripProgressRepository
                .findByItineraryPlanPlanIdOrderByProgressIdAsc(planId)
                .stream()
                .anyMatch(progress ->
                        "IN_PROGRESS".equals(progress.getStatus()));
    }

    // =========================================================
    // HÀM PHỤ — GÁN THÔNG TIN KẾ HOẠCH
    // =========================================================

    private void applyFields(
            TravelPlan plan,
            TravelPlanDtos.PlanFields fields) {

        if (fields.startDate() == null
                || fields.endDate() == null
                || fields.endDate().isBefore(fields.startDate())) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    INVALID_DATES);
        }

        plan.setTitle(trimmed(fields.title()));
        plan.setDestination(trimmed(fields.destination()));
        plan.setStartDate(fields.startDate());
        plan.setEndDate(fields.endDate());
        plan.setBudget(fields.budget());

        plan.setCurrency(
                fields.currency() == null
                        ? "VND"
                        : fields.currency().toUpperCase(Locale.ROOT));

        plan.setGroupSize(
                fields.groupSize() == null
                        ? 1
                        : fields.groupSize());

        plan.setDescription(fields.description());

        if (fields.experiencePreferences() != null) {
            plan.setExperiencePreferences(new ArrayList<>(
                    validateExperiencePreferences(fields.experiencePreferences())));
        }

        plan.setCategoryCodes(
                new ArrayList<>(
                        validateCategoryCodes(fields.categoryCodes())));

        plan.setTravelPace(
                fields.travelPace() == null
                        ? "BALANCED"
                        : fields.travelPace());
    }

    // =========================================================
    // HÀM PHỤ — DANH MỤC TRẢI NGHIỆM
    // =========================================================

    private static List<String> validateExperiencePreferences(List<String> codes) {
        if (codes.size() > EXPERIENCE_CODES.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Chỉ được chọn tối đa " + EXPERIENCE_CODES.size() + " sở thích trải nghiệm");
        }
        checkCodeList(codes, "Sở thích trải nghiệm");
        for (String code : codes) {
            if (!EXPERIENCE_CODES.contains(code)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Sở thích trải nghiệm không hợp lệ: " + code);
            }
        }
        return List.copyOf(codes);
    }

    private List<String> validateCategoryCodes(
            List<String> requestedCodes) {

        if (requestedCodes == null
                || requestedCodes.isEmpty()) {
            return List.of();
        }

        checkCodeList(
                requestedCodes,
                "Danh mục trải nghiệm");

        Set<String> validCodes =
                categoryRepository
                        .findByStatusOrderByCategoryIdAsc("ACTIVE")
                        .stream()
                        .filter(category -> category.getParent() != null)
                        .filter(category ->
                                "ACTIVE".equals(
                                        category.getParent().getStatus()))
                        .map(PlaceCategory::getCode)
                        .collect(Collectors.toSet());

        for (String code : requestedCodes) {
            if (!validCodes.contains(code)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Danh mục trải nghiệm không hợp lệ: " + code);
            }
        }

        return List.copyOf(requestedCodes);
    }

    private static void checkCodeList(
            List<String> codes,
            String label) {

        if (codes.size() > MAX_CODES) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    label + ": chỉ được chọn tối đa "
                            + MAX_CODES + " mục");
        }

        Set<String> seen = new HashSet<>();

        for (String code : codes) {
            if (code == null
                    || code.isBlank()
                    || code.length() > MAX_CODES) {

                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        label + " không hợp lệ: " + code);
            }

            if (!seen.add(code)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        label + " bị trùng: " + code);
            }
        }
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    // =========================================================
    // HÀM PHỤ — TRẠNG THÁI
    // =========================================================

    private static String label(String status) {

        return switch (status) {
            case TravelPlanStatus.DRAFT -> "Nháp";
            case TravelPlanStatus.PLANNED -> "Đã lên kế hoạch";
            case TravelPlanStatus.IN_PROGRESS -> "Đang diễn ra";
            case TravelPlanStatus.COMPLETED -> "Hoàn thành";
            case TravelPlanStatus.CANCELLED -> "Đã hủy";
            default -> String.valueOf(status);
        };
    }

    private static String transitionHint(String current) {

        List<String> next = allowedManualNext(current);

        if (next.isEmpty()) {
            return TravelPlanStatus.IN_PROGRESS.equals(current)
                    ? ". Hãy hoàn thành chuyến đi qua chức năng theo dõi tiến độ"
                    : ". Kế hoạch đã kết thúc, không thể đổi trạng thái nữa";
        }

        return ". Chỉ được chuyển sang: "
                + next.stream()
                .map(TravelPlanService::label)
                .collect(Collectors.joining(", "));
    }

    /**
     * Chỉ trả những trạng thái được phép đổi
     * thông qua PATCH /status.
     */
    private static List<String> allowedManualNext(
            String current) {

        if (TravelPlanStatus.DRAFT.equals(current)) {
            return List.of(
                    TravelPlanStatus.PLANNED,
                    TravelPlanStatus.CANCELLED);
        }

        if (TravelPlanStatus.PLANNED.equals(current)) {
            return List.of(TravelPlanStatus.CANCELLED);
        }

        return List.of();
    }

    // =========================================================
    // CHUYỂN ENTITY SANG DTO
    // =========================================================

    private TravelPlanDtos.PlanResponse toResponse(
            TravelPlan plan) {

        List<String> categories =
                plan.getCategoryCodes() == null
                        ? List.of()
                        : List.copyOf(plan.getCategoryCodes());

        return new TravelPlanDtos.PlanResponse(
                plan.getPlanId(),
                plan.getUser().getUserId(),
                plan.getTitle(),
                plan.getDestination(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getBudget(),
                plan.getCurrency(),
                plan.getGroupSize(),
                plan.getDescription(),
                categories,
                plan.getTravelPace(),
                plan.getStatus(),
                allowedManualNext(plan.getStatus()),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                plan.getExperiencePreferences() == null
                        ? List.of()
                        : List.copyOf(plan.getExperiencePreferences()),
                estimatedCostOf(plan.getPlanId()));
    }

    /**
     * Tổng chi phí Backend tính được cho kế hoạch: ưu tiên lịch trình ACTIVE (view
     * itinerary_cost_summary, chính xác hơn vì có đủ hoạt động từng ngày); chưa có lịch trình thì
     * ước tính tạm bằng tổng giá tham khảo của các địa điểm đã chọn. Null nếu không có dữ liệu nào.
     */
    private BigDecimal estimatedCostOf(Long planId) {
        BigDecimal fromItinerary = itineraryRepository
                .findFirstByPlanPlanIdAndStatusOrderByVersionNumberDesc(planId, "ACTIVE")
                .flatMap(itinerary ->
                        itinerarySummaryRepository.findCostSummary(itinerary.getItineraryId()))
                .map(ItinerarySummaryRepository.CostSummary::totalEstimatedCost)
                .orElse(null);

        return fromItinerary != null
                ? fromItinerary
                : estimatedCostFromSelectedPlaces(planId);
    }

    /**
     * Chưa có lịch trình: cộng giá tham khảo thấp nhất (VND) của từng địa điểm đã chọn. Địa điểm
     * chưa có giá thì bỏ qua (không tính là 0đ); không có địa điểm nào có giá thì trả về null.
     */
    private BigDecimal estimatedCostFromSelectedPlaces(Long planId) {
        List<PlanPlace> selectedPlaces =
                planPlaceRepository.findByPlanPlanIdOrderByPriorityDescPlanPlaceIdAsc(planId);
        if (selectedPlaces.isEmpty()) {
            return null;
        }

        List<Long> placeIds =
                selectedPlaces.stream().map(p -> p.getPlace().getPlaceId()).toList();

        Map<Long, BigDecimal> cheapestByPlace =
                placePriceRepository.findByPlacePlaceIdInOrderByPriceIdAsc(placeIds).stream()
                        .filter(price -> "VND".equals(price.getCurrency()))
                        .collect(
                                Collectors.toMap(
                                        price -> price.getPlace().getPlaceId(),
                                        PlacePrice::getAmount,
                                        BigDecimal::min));

        if (cheapestByPlace.isEmpty()) {
            return null;
        }
        return cheapestByPlace.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
