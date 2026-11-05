package com.dtrps.modules.itinerary.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.ItineraryDtos;
import com.dtrps.modules.itinerary.entity.Itinerary;
import com.dtrps.modules.itinerary.entity.ItineraryDay;
import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.entity.PlanPlace;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.ItineraryDayRepository;
import com.dtrps.modules.itinerary.repository.ItineraryRepository;
import com.dtrps.modules.itinerary.repository.PlanPlaceRepository;
import com.dtrps.modules.itinerary.repository.ItineraryItemRepository;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryService {

    private final TravelPlanRepository planRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository dayRepository;
    private final PlanPlaceRepository planPlaces;
    private final ItineraryItemRepository itemRepository;
    private final ItinerarySchedulingService scheduler;

    public ItineraryService(
            TravelPlanRepository planRepository,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository dayRepository,
            PlanPlaceRepository planPlaces,
            ItineraryItemRepository itemRepository,
            ItinerarySchedulingService scheduler
    ) {
        this.planRepository = planRepository;
        this.itineraryRepository = itineraryRepository;
        this.dayRepository = dayRepository;
        this.planPlaces = planPlaces;
        this.itemRepository = itemRepository;
        this.scheduler = scheduler;
    }

    /**
     * TASK 45 - Tạo lịch trình theo ngày.
     *
     * 1. Kiểm tra kế hoạch thuộc người dùng.
     * 2. Xác định version_number tiếp theo.
     * 3. Chuyển lịch trình ACTIVE cũ sang ARCHIVED.
     * 4. Tạo lịch trình ACTIVE mới.
     * 5. Tự động tạo các ngày.
     */
    @Transactional
    public ItineraryDtos.ItineraryResponse create(
            Long userId,
            Long planId,
            ItineraryDtos.CreateRequest request
    ) {

        // Bước 1: Lấy kế hoạch và khóa trong transaction
        TravelPlan plan = planRepository.lockById(planId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy kế hoạch du lịch"
                ));

        // Không được tạo lịch trình cho kế hoạch người khác
        checkOwner(plan, userId);

        LocalDate startDate = plan.getStartDate();
        LocalDate endDate = plan.getEndDate();

        if (
                startDate == null ||
                        endDate == null ||
                        endDate.isBefore(startDate)
        ) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày bắt đầu và kết thúc không hợp lệ"
            );
        }

        // Bước 2: Tính tổng số ngày, bao gồm cả ngày đi
        long totalDays = ChronoUnit.DAYS.between(
                startDate,
                endDate
        ) + 1;

        if (totalDays <= 0 || totalDays > Integer.MAX_VALUE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Số ngày của kế hoạch không hợp lệ"
            );
        }

        // Chỉ những địa điểm Traveller đã chọn mới được đưa vào lịch trình.
        List<PlanPlace> chosen = planPlaces.findByPlanPlanIdOrderByPriorityDescPlanPlaceIdAsc(planId);
        // Lên lịch trong bộ nhớ trước: lỗi không làm thay đổi ACTIVE hiện tại.
        List<List<ItinerarySchedulingService.Visit>> schedule = scheduler.arrange(plan, chosen);

        // Bước 3: Xác định version_number tiếp theo
        int nextVersion = itineraryRepository
                .findTopByPlanPlanIdOrderByVersionNumberDesc(planId)
                .map(previous -> {
                    int version = previous.getVersionNumber();

                    if (version == Integer.MAX_VALUE) {
                        throw new ApiException(
                                HttpStatus.CONFLICT,
                                "Không thể tạo thêm phiên bản lịch trình"
                        );
                    }

                    return version + 1;
                })
                .orElse(1);

        // Bước 4: Lấy tất cả lịch trình ACTIVE cũ
        List<Itinerary> activeItineraries =
                itineraryRepository.findByPlanPlanIdAndStatus(
                        planId,
                        "ACTIVE"
                );

        // Chuyển các bản cũ sang ARCHIVED
        for (Itinerary active : activeItineraries) {
            active.setStatus("ARCHIVED");
        }

        // Flush trước khi tạo bản ACTIVE mới
        itineraryRepository.saveAllAndFlush(activeItineraries);

        // Bước 5: Tạo lịch trình mới
        Itinerary itinerary = new Itinerary();

        itinerary.setPlan(plan);
        itinerary.setVersionNumber(nextVersion);

        String title = request.title();

        if (title == null || title.isBlank()) {
            title = "Lịch trình " + nextVersion;
        }

        itinerary.setTitle(title.trim());
        itinerary.setNotes(request.notes());
        itinerary.setGenerationSource("SYSTEM");
        itinerary.setStatus("ACTIVE");
        itinerary.setCurrency(plan.getCurrency());

        itinerary = itineraryRepository.saveAndFlush(itinerary);

        // Bước 6: Tự động sinh các ngày
        List<ItineraryDay> days = new ArrayList<>();

        for (int dayNumber = 1; dayNumber <= totalDays; dayNumber++) {

            ItineraryDay day = new ItineraryDay();

            day.setItinerary(itinerary);
            day.setDayNumber(dayNumber);
            day.setTitle("Ngày " + dayNumber);
            day.setNotes(null);

            days.add(day);
        }

        // Lưu các ngày vào itinerary_days
        days = dayRepository.saveAllAndFlush(days);

        // Lưu các hoạt động được hệ thống tự động sắp xếp.
        List<ItineraryItem> items = new ArrayList<>();
        for (int d = 0; d < days.size(); d++) {
            int position = 1;
            for (ItinerarySchedulingService.Visit visit : schedule.get(d)) {
                ItineraryItem item = new ItineraryItem();
                item.setDay(days.get(d));
                item.setPlace(visit.place());
                item.setItemOrder(position++);
                item.setStartTime(visit.start());
                item.setEndTime(visit.end());
                item.setTravelTimeMinutes(visit.travelMinutes());
                item.setEstimatedCost(visit.estimatedCost());
                item.setStatus("PLANNED");
                items.add(item);
            }
        }
        itemRepository.saveAllAndFlush(items);
        return toResponse(itinerary, days);
    }

    /**
     * Lấy lịch trình ACTIVE của một kế hoạch.
     */
    @Transactional(readOnly = true)
    public ItineraryDtos.ItineraryResponse getActive(
            Long userId,
            Long planId
    ) {

        TravelPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy kế hoạch du lịch"
                ));

        checkOwner(plan, userId);

        Itinerary itinerary = itineraryRepository
                .findFirstByPlanPlanIdAndStatusOrderByVersionNumberDesc(
                        planId,
                        "ACTIVE"
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Kế hoạch chưa có lịch trình ACTIVE"
                ));

        List<ItineraryDay> days =
                dayRepository
                        .findByItineraryItineraryIdOrderByDayNumberAsc(
                                itinerary.getItineraryId()
                        );

        return toResponse(itinerary, days);
    }

    /**
     * Chỉ chủ sở hữu kế hoạch được phép truy cập.
     */
    private void checkOwner(
            TravelPlan plan,
            Long userId
    ) {

        Long ownerId = plan.getUser().getUserId();

        if (!ownerId.equals(userId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy kế hoạch du lịch"
            );
        }
    }

    /**
     * Chuyển entity sang DTO.
     *
     * visitDate không được lưu trong itinerary_days.
     * Nó được tính từ startDate + dayNumber - 1.
     */
    private ItineraryDtos.ItineraryResponse toResponse(
            Itinerary itinerary,
            List<ItineraryDay> days
    ) {

        TravelPlan plan = itinerary.getPlan();

        List<ItineraryDtos.DayResponse> dayResponses =
                days.stream()
                        .map(day -> {

                            LocalDate visitDate = plan.getStartDate()
                                    .plusDays(day.getDayNumber() - 1L);

                            return new ItineraryDtos.DayResponse(
                                    day.getDayId(),
                                    day.getDayNumber(),
                                    visitDate,
                                    day.getTitle(),
                                    day.getNotes()
                            );
                        })
                        .toList();

        return new ItineraryDtos.ItineraryResponse(
                itinerary.getItineraryId(),
                plan.getPlanId(),
                itinerary.getTitle(),
                itinerary.getVersionNumber(),
                itinerary.getStatus(),
                itinerary.getCurrency(),
                plan.getStartDate(),
                plan.getEndDate(),
                dayResponses
        );
    }
}