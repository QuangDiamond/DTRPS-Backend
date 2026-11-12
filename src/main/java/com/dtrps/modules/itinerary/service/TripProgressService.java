
package com.dtrps.modules.itinerary.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.TripProgressDtos;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý việc thực hiện lịch trình theo từng ngày.
 *
 * Phần này chỉ lưu tiến độ. GPS được lấy tại Frontend
 * và tuyến đường được tính bằng MapController/MapService.
 *
 * Các thao tác ghi đều khóa TravelPlan để tránh
 * hai request đồng thời tạo bản ghi tiến độ trùng.
 */
@Service
public class TripProgressService {

    private static final String NOT_STARTED = "NOT_STARTED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";
    private static final String PENDING = "PENDING";
    private static final String SKIPPED = "SKIPPED";

    private final TravelPlanRepository planRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository dayRepository;
    private final ItineraryItemRepository itemRepository;
    private final TripProgressRepository progressRepository;
    private final TripDayProgressRepository dayProgressRepository;
    private final ActivityProgressRepository activityProgressRepository;

    public TripProgressService(
            TravelPlanRepository planRepository,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository dayRepository,
            ItineraryItemRepository itemRepository,
            TripProgressRepository progressRepository,
            TripDayProgressRepository dayProgressRepository,
            ActivityProgressRepository activityProgressRepository) {

        this.planRepository = planRepository;
        this.itineraryRepository = itineraryRepository;
        this.dayRepository = dayRepository;
        this.itemRepository = itemRepository;
        this.progressRepository = progressRepository;
        this.dayProgressRepository = dayProgressRepository;
        this.activityProgressRepository = activityProgressRepository;
    }

    /**
     * Xem tiến độ của lịch trình ACTIVE.
     * Nếu chưa bắt đầu, trả NOT_STARTED.
     */
    @Transactional(readOnly = true)
    public TripProgressDtos.ProgressResponse getProgress(
            Long userId,
            Long planId) {

        TravelPlan plan = planRepository
                .findByPlanIdAndUserUserId(planId, userId)
                .orElseThrow(() ->
                        notFound("Không tìm thấy kế hoạch du lịch"));

        Itinerary itinerary = findActiveItinerary(planId);

        TripProgress progress = progressRepository
                .findByItineraryItineraryId(itinerary.getItineraryId())
                .orElse(null);

        return toResponse(plan, itinerary, progress);
    }

    /**
     * Bắt đầu một ngày theo dayNumber.
     *
     * Không cho bắt đầu ngày mới khi ngày trước còn dang dở.
     * Nhấn lại ngày đang chạy sẽ trả tiến độ hiện tại.
     *
     * Ngày không có hoạt động vẫn được bắt đầu,
     * tránh kẹt tiến độ toàn chuyến.
     */
    @Transactional
    public TripProgressDtos.ProgressResponse startDay(
            Long userId,
            Long planId,
            Integer dayNumber) {

        TravelPlan plan = lockOwnedPlan(userId, planId);

        Itinerary itinerary = findActiveItinerary(planId);

        ItineraryDay day = findDay(itinerary, dayNumber);

        // Ngày không có hoạt động vẫn thuộc Itinerary
        // và có thể được bắt đầu.
        // Frontend không gọi Goong Directions khi ngày rỗng.

        if (CANCELLED.equals(plan.getStatus())
                || COMPLETED.equals(plan.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Kế hoạch đã kết thúc hoặc bị hủy");
        }

        TripProgress progress = progressRepository
                .lockByItineraryId(itinerary.getItineraryId())
                .orElse(null);

        if (progress != null
                && (COMPLETED.equals(progress.getStatus())
                || CANCELLED.equals(progress.getStatus()))) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Chuyến đi đã kết thúc");
        }

        // Ngày trước phải hoàn thành trước khi bắt đầu Ngày N.
        if (dayNumber > 1) {

            List<ItineraryDay> days = dayRepository
                    .findByItineraryItineraryIdOrderByDayNumberAsc(
                            itinerary.getItineraryId());

            for (ItineraryDay previous : days) {

                if (previous.getDayNumber() >= dayNumber) {
                    break;
                }

                boolean done = progress != null
                        && dayProgressRepository
                        .findByProgressProgressIdAndDayDayId(
                                progress.getProgressId(),
                                previous.getDayId())
                        .map(p -> COMPLETED.equals(p.getStatus()))
                        .orElse(false);

                if (!done) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "Hãy hoàn thành Ngày "
                                    + previous.getDayNumber()
                                    + " trước");
                }
            }
        }

        // Tạo tiến độ toàn chuyến nếu chưa có.
        if (progress == null) {

            progress = new TripProgress();
            progress.setItinerary(itinerary);
            progress.setStatus(NOT_STARTED);

            progress = progressRepository.saveAndFlush(progress);
        }

        TripDayProgress dayProgress = dayProgressRepository
                .lockByProgressIdAndDayId(
                        progress.getProgressId(),
                        day.getDayId())
                .orElse(null);

        // Nhấn bắt đầu nhiều lần không tạo bản ghi trùng.
        if (dayProgress != null
                && IN_PROGRESS.equals(dayProgress.getStatus())) {

            return toResponse(plan, itinerary, progress);
        }

        if (dayProgress != null
                && COMPLETED.equals(dayProgress.getStatus())) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Ngày này đã hoàn thành");
        }

        // Không cho hai ngày chạy đồng thời.
        if (dayProgressRepository
                .existsByProgressProgressIdAndStatus(
                        progress.getProgressId(),
                        IN_PROGRESS)) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Hãy hoàn thành ngày đang thực hiện "
                            + "trước khi bắt đầu ngày khác");
        }

        Instant now = Instant.now();

        if (dayProgress == null) {
            dayProgress = new TripDayProgress();
            dayProgress.setProgress(progress);
            dayProgress.setDay(day);
        }

        dayProgress.setStatus(IN_PROGRESS);
        dayProgress.setStartedAt(now);
        dayProgress.setCompletedAt(null);

        dayProgressRepository.saveAndFlush(dayProgress);

        // Chuyển tiến độ toàn chuyến sang IN_PROGRESS.
        if (NOT_STARTED.equals(progress.getStatus())) {

            progress.setStatus(IN_PROGRESS);
            progress.setStartedAt(now);

            progressRepository.saveAndFlush(progress);
        }

        // Đồng bộ TravelPlan.
        if (TravelPlanStatus.DRAFT.equals(plan.getStatus())) {
            plan.setStatus(TravelPlanStatus.PLANNED);
        }

        if (TravelPlanStatus.PLANNED.equals(plan.getStatus())) {
            plan.setStatus(TravelPlanStatus.IN_PROGRESS);
        }

        planRepository.saveAndFlush(plan);

        return toResponse(plan, itinerary, progress);
    }

    /**
     * Đánh dấu một địa điểm trong ngày:
     * COMPLETED hoặc SKIPPED.
     */
    @Transactional
    public TripProgressDtos.ProgressResponse updateActivity(
            Long userId,
            Long planId,
            Integer dayNumber,
            Long itemId,
            TripProgressDtos.UpdateActivityRequest request) {

        if (request == null
                || !(COMPLETED.equals(request.status())
                || SKIPPED.equals(request.status()))) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Trạng thái hoạt động phải là COMPLETED hoặc SKIPPED");
        }

        TravelPlan plan = lockOwnedPlan(userId, planId);

        requirePlanRunning(plan);

        Itinerary itinerary = findActiveItinerary(planId);
        ItineraryDay day = findDay(itinerary, dayNumber);

        TripProgress progress = requireRunningTrip(itinerary);

        requireRunningDay(progress, day);

        ItineraryItem item = itemRepository.findById(itemId)
                .orElseThrow(() ->
                        notFound("Không tìm thấy hoạt động"));

        if (!Objects.equals(
                item.getDay().getDayId(),
                day.getDayId())) {

            throw notFound("Hoạt động không thuộc ngày đã chọn");
        }

        ActivityProgress activity = activityProgressRepository
                .lockByProgressIdAndItemId(
                        progress.getProgressId(),
                        itemId)
                .orElse(null);

        if (activity == null) {
            activity = new ActivityProgress();
            activity.setProgress(progress);
            activity.setItem(item);
        }

        activity.setStatus(request.status());

        activity.setCompletedAt(
                COMPLETED.equals(request.status())
                        ? Instant.now()
                        : null);

        String notes = request.notes();

        activity.setNotes(
                notes == null || notes.isBlank()
                        ? null
                        : notes.trim());

        activityProgressRepository.saveAndFlush(activity);

        // Không sửa ItineraryItem.status.
        // Lịch trình dự kiến và tiến độ thực tế được lưu riêng.
        return toResponse(plan, itinerary, progress);
    }

    /**
     * Hoàn thành ngày khi tất cả địa điểm
     * được COMPLETED hoặc SKIPPED.
     *
     * Ngày rỗng có thể hoàn thành ngay sau khi bắt đầu.
     *
     * Nếu tất cả ngày hoàn thành,
     * kết thúc TripProgress và TravelPlan.
     */
    @Transactional
    public TripProgressDtos.ProgressResponse completeDay(
            Long userId,
            Long planId,
            Integer dayNumber) {

        TravelPlan plan = lockOwnedPlan(userId, planId);

        requirePlanRunning(plan);

        Itinerary itinerary = findActiveItinerary(planId);
        ItineraryDay day = findDay(itinerary, dayNumber);

        TripProgress progress = requireRunningTrip(itinerary);

        TripDayProgress dayProgress =
                requireRunningDay(progress, day);

        // Một ngày có thể không có hoạt động.
        // Khi đó items.size() = 0 và completed + skipped = 0.
        List<ItineraryItem> items =
                itemRepository.findOrderedByDayId(day.getDayId());

        long completed = activityProgressRepository
                .countByProgressIdAndDayIdAndStatus(
                        progress.getProgressId(),
                        day.getDayId(),
                        COMPLETED);

        long skipped = activityProgressRepository
                .countByProgressIdAndDayIdAndStatus(
                        progress.getProgressId(),
                        day.getDayId(),
                        SKIPPED);

        if (completed + skipped != items.size()) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Hãy đánh dấu hoàn thành hoặc bỏ qua "
                            + "tất cả địa điểm trước");
        }

        Instant now = Instant.now();

        dayProgress.setStatus(COMPLETED);
        dayProgress.setCompletedAt(now);

        dayProgressRepository.saveAndFlush(dayProgress);

        List<ItineraryDay> allDays = dayRepository
                .findByItineraryItineraryIdOrderByDayNumberAsc(
                        itinerary.getItineraryId());

        List<TripDayProgress> trackedDays = dayProgressRepository
                .findByProgressProgressIdOrderByDayDayNumberAsc(
                        progress.getProgressId());

        long completedDays = trackedDays.stream()
                .filter(p -> COMPLETED.equals(p.getStatus()))
                .count();

        // Nếu mọi ngày hoàn thành thì kết thúc toàn chuyến.
        if (completedDays == allDays.size()) {

            progress.setStatus(COMPLETED);
            progress.setCompletedAt(now);

            progressRepository.saveAndFlush(progress);

            plan.setStatus(TravelPlanStatus.COMPLETED);

            planRepository.saveAndFlush(plan);
        }

        return toResponse(plan, itinerary, progress);
    }

    /**
     * Khóa kế hoạch và xác minh chủ sở hữu.
     */
    private TravelPlan lockOwnedPlan(
            Long userId,
            Long planId) {

        TravelPlan plan = planRepository.lockById(planId)
                .orElseThrow(() ->
                        notFound("Không tìm thấy kế hoạch du lịch"));

        if (!Objects.equals(
                plan.getUser().getUserId(),
                userId)) {

            throw notFound("Không tìm thấy kế hoạch du lịch");
        }

        return plan;
    }

    /**
     * Lấy phiên bản lịch trình ACTIVE.
     */
    private Itinerary findActiveItinerary(Long planId) {

        return itineraryRepository
                .findFirstByPlanPlanIdAndStatusOrderByVersionNumberDesc(
                        planId,
                        "ACTIVE")
                .orElseThrow(() ->
                        notFound("Kế hoạch chưa có lịch trình ACTIVE"));
    }

    /**
     * Tìm ngày theo số thứ tự.
     */
    private ItineraryDay findDay(
            Itinerary itinerary,
            Integer dayNumber) {

        if (dayNumber == null || dayNumber < 1) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Số ngày không hợp lệ");
        }

        return dayRepository
                .findByItineraryItineraryIdOrderByDayNumberAsc(
                        itinerary.getItineraryId())
                .stream()
                .filter(day ->
                        Objects.equals(day.getDayNumber(), dayNumber))
                .findFirst()
                .orElseThrow(() ->
                        notFound("Không tìm thấy ngày trong lịch trình"));
    }

    /**
     * Kiểm tra TravelPlan đang thực hiện.
     */
    private void requirePlanRunning(TravelPlan plan) {

        if (!TravelPlanStatus.IN_PROGRESS.equals(plan.getStatus())) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Kế hoạch không ở trạng thái đang diễn ra");
        }
    }

    /**
     * Lấy tiến độ toàn chuyến đang chạy.
     */
    private TripProgress requireRunningTrip(
            Itinerary itinerary) {

        TripProgress progress = progressRepository
                .lockByItineraryId(itinerary.getItineraryId())
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.CONFLICT,
                                "Bạn chưa bắt đầu chuyến đi"));

        if (!IN_PROGRESS.equals(progress.getStatus())) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Chuyến đi không ở trạng thái đang diễn ra");
        }

        return progress;
    }

    /**
     * Lấy tiến độ ngày đang chạy.
     */
    private TripDayProgress requireRunningDay(
            TripProgress progress,
            ItineraryDay day) {

        TripDayProgress dayProgress = dayProgressRepository
                .lockByProgressIdAndDayId(
                        progress.getProgressId(),
                        day.getDayId())
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.CONFLICT,
                                "Bạn chưa bắt đầu ngày này"));

        if (!IN_PROGRESS.equals(dayProgress.getStatus())) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Ngày này không ở trạng thái đang diễn ra");
        }

        return dayProgress;
    }

    /**
     * Chuyển dữ liệu tiến độ thành DTO.
     */
    private TripProgressDtos.ProgressResponse toResponse(
            TravelPlan plan,
            Itinerary itinerary,
            TripProgress progress) {

        Map<Long, TripDayProgress> dayStates = new HashMap<>();
        Map<Long, ActivityProgress> activityStates = new HashMap<>();

        if (progress != null) {

            for (TripDayProgress p :
                    dayProgressRepository
                            .findByProgressProgressIdOrderByDayDayNumberAsc(
                                    progress.getProgressId())) {

                dayStates.put(p.getDay().getDayId(), p);
            }

            for (ActivityProgress p :
                    activityProgressRepository
                            .findOrderedByProgressId(
                                    progress.getProgressId())) {

                activityStates.put(p.getItem().getItemId(), p);
            }
        }

        List<TripProgressDtos.DayResponse> days =
                new ArrayList<>();

        for (ItineraryDay day :
                dayRepository
                        .findByItineraryItineraryIdOrderByDayNumberAsc(
                                itinerary.getItineraryId())) {

            TripDayProgress dayState =
                    dayStates.get(day.getDayId());

            List<TripProgressDtos.ActivityResponse> activities =
                    new ArrayList<>();

            for (ItineraryItem item :
                    itemRepository.findOrderedByDayId(day.getDayId())) {

                ActivityProgress state =
                        activityStates.get(item.getItemId());

                activities.add(
                        new TripProgressDtos.ActivityResponse(
                                state == null
                                        ? null
                                        : state.getActivityProgressId(),
                                item.getItemId(),
                                item.getItemOrder(),
                                item.getPlace().getPlaceId(),
                                item.getPlace().getName(),
                                state == null
                                        ? PENDING
                                        : state.getStatus(),
                                state == null
                                        ? null
                                        : state.getCompletedAt(),
                                state == null
                                        ? null
                                        : state.getNotes()));
            }

            days.add(
                    new TripProgressDtos.DayResponse(
                            dayState == null
                                    ? null
                                    : dayState.getDayProgressId(),
                            day.getDayId(),
                            day.getDayNumber(),
                            plan.getStartDate().plusDays(
                                    day.getDayNumber() - 1L),
                            day.getTitle(),
                            dayState == null
                                    ? NOT_STARTED
                                    : dayState.getStatus(),
                            dayState == null
                                    ? null
                                    : dayState.getStartedAt(),
                            dayState == null
                                    ? null
                                    : dayState.getCompletedAt(),
                            activities));
        }

        return new TripProgressDtos.ProgressResponse(
                progress == null
                        ? null
                        : progress.getProgressId(),
                plan.getPlanId(),
                itinerary.getItineraryId(),
                progress == null
                        ? NOT_STARTED
                        : progress.getStatus(),
                progress == null
                        ? null
                        : progress.getStartedAt(),
                progress == null
                        ? null
                        : progress.getCompletedAt(),
                days);
    }

    private static ApiException notFound(String message) {

        return new ApiException(
                HttpStatus.NOT_FOUND,
                message);
    }
}
