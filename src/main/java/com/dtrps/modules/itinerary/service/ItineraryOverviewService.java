
package com.dtrps.modules.itinerary.service;

import com.dtrps.exception.ApiException;

import com.dtrps.modules.itinerary.dto.ItineraryOverviewDtos;

import com.dtrps.modules.itinerary.entity.Itinerary;
import com.dtrps.modules.itinerary.entity.ItineraryDay;
import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.entity.TravelPlan;

import com.dtrps.modules.itinerary.repository.ItineraryRepository;
import com.dtrps.modules.itinerary.repository.ItineraryDayRepository;
import com.dtrps.modules.itinerary.repository.ItineraryItemRepository;
import com.dtrps.modules.itinerary.repository.ItinerarySummaryRepository;

import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.TransportMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryOverviewService {

    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository dayRepository;
    private final ItineraryItemRepository itemRepository;
    private final ItinerarySummaryRepository summaryRepository;

    public ItineraryOverviewService(
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository dayRepository,
            ItineraryItemRepository itemRepository,
            ItinerarySummaryRepository summaryRepository
    ) {
        this.itineraryRepository = itineraryRepository;
        this.dayRepository = dayRepository;
        this.itemRepository = itemRepository;
        this.summaryRepository = summaryRepository;
    }

    /**
     * TASK 47
     * Xem lịch trình ACTIVE và tổng chi phí.
     */
    @Transactional(readOnly = true)
    public ItineraryOverviewDtos.OverviewResponse
    getActiveOverview(
            Long userId,
            Long planId
    ) {

        Itinerary itinerary = itineraryRepository
                .findFirstByPlanPlanIdAndStatusOrderByVersionNumberDesc(
                        planId,
                        "ACTIVE"
                )
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lịch trình ACTIVE"
                ));

        TravelPlan plan = itinerary.getPlan();

        checkOwnerAndPlan(plan, userId, planId);

        return buildOverview(itinerary);
    }

    /**
     * TASK 47
     * Xem một phiên bản lịch trình cụ thể.
     *
     * Cho phép xem ACTIVE và ARCHIVED,
     * nhưng chỉ khi thuộc đúng kế hoạch và người dùng.
     */
    @Transactional(readOnly = true)
    public ItineraryOverviewDtos.OverviewResponse
    getOverviewById(
            Long userId,
            Long planId,
            Long itineraryId
    ) {

        Itinerary itinerary = itineraryRepository
                .findById(itineraryId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lịch trình"
                ));

        TravelPlan plan = itinerary.getPlan();

        checkOwnerAndPlan(plan, userId, planId);

        return buildOverview(itinerary);
    }

    /**
     * Kiểm tra lịch trình thuộc đúng kế hoạch
     * và đúng tài khoản đang đăng nhập.
     */
    private void checkOwnerAndPlan(
            TravelPlan plan,
            Long userId,
            Long planId
    ) {

        if (!Objects.equals(plan.getPlanId(), planId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy lịch trình trong kế hoạch"
            );
        }

        Long ownerId = plan.getUser().getUserId();

        if (!Objects.equals(ownerId, userId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy lịch trình trong kế hoạch"
            );
        }
    }

    /**
     * Tổng hợp lịch trình, ngày, hoạt động
     * và chi phí dựa trên các database view.
     */
    private ItineraryOverviewDtos.OverviewResponse buildOverview(
            Itinerary itinerary
    ) {

        Long itineraryId = itinerary.getItineraryId();
        TravelPlan plan = itinerary.getPlan();

        // Bước 1: Lấy các ngày trong lịch trình
        List<ItineraryDay> days = dayRepository
                .findByItineraryItineraryIdOrderByDayNumberAsc(
                        itineraryId
                );

        // Bước 2: Lấy chi phí và ngày tham quan từ VIEW
        List<ItinerarySummaryRepository.DaySummary> summaries =
                summaryRepository.findDaySummaries(itineraryId);

        Map<Long, ItinerarySummaryRepository.DaySummary>
                summaryByDayId = new HashMap<>();

        for (
                ItinerarySummaryRepository.DaySummary summary :
                summaries
        ) {
            summaryByDayId.put(
                    summary.dayId(),
                    summary
            );
        }

        // Bước 3: Lấy tổng chi phí toàn chuyến từ VIEW
        ItinerarySummaryRepository.CostSummary costSummary =
                summaryRepository.findCostSummary(itineraryId)
                        .orElseThrow(() -> new ApiException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Không tìm thấy dữ liệu tổng hợp chi phí"
                        ));

        // Bước 4: Tạo danh sách ngày kèm hoạt động
        List<ItineraryOverviewDtos.DayResponse> dayResponses =
                new ArrayList<>();

        for (ItineraryDay day : days) {

            ItinerarySummaryRepository.DaySummary summary =
                    summaryByDayId.get(day.getDayId());

            if (summary == null) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Thiếu dữ liệu tổng hợp cho ngày "
                                + day.getDayNumber()
                );
            }

            // Lấy hoạt động theo item_order tăng dần
            List<ItineraryItem> items =
                    itemRepository.findOrderedByDayId(day.getDayId());

            List<ItineraryOverviewDtos.ActivityResponse>
                    activityResponses = new ArrayList<>();

            for (ItineraryItem item : items) {
                activityResponses.add(toActivityResponse(item));
            }

            // estimatedCost được lấy trực tiếp từ VIEW
            ItineraryOverviewDtos.DayResponse dayResponse =
                    new ItineraryOverviewDtos.DayResponse(
                            day.getDayId(),
                            day.getDayNumber(),
                            summary.visitDate(),
                            day.getTitle(),
                            day.getNotes(),
                            summary.estimatedCost(),
                            activityResponses
                    );

            dayResponses.add(dayResponse);
        }

        // Bước 5: Trả về response tổng hợp
        return new ItineraryOverviewDtos.OverviewResponse(
                itinerary.getItineraryId(),
                plan.getPlanId(),
                plan.getTitle(),
                plan.getDestination(),
                itinerary.getTitle(),
                itinerary.getVersionNumber(),
                itinerary.getGenerationSource(),
                itinerary.getStatus(),
                costSummary.currency(),
                itinerary.getNotes(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getBudget(),
                costSummary.totalEstimatedCost(),
                dayResponses
        );
    }

    /**
     * Chuyển ItineraryItem thành ActivityResponse.
     */
    private ItineraryOverviewDtos.ActivityResponse
    toActivityResponse(
            ItineraryItem item
    ) {

        Place place = item.getPlace();
        TransportMode transport = item.getTransportMode();

        Long transportModeId = null;
        String transportModeName = null;

        if (transport != null) {
            transportModeId = transport.getTransportModeId();
            transportModeName = transport.getName();
        }

        return new ItineraryOverviewDtos.ActivityResponse(
                item.getItemId(),
                item.getItemOrder(),
                place.getPlaceId(),
                place.getName(),
                place.getAddress(),
                item.getStartTime(),
                item.getEndTime(),
                item.getEstimatedCost(),
                transportModeId,
                transportModeName,
                item.getTravelTimeMinutes(),
                item.getNotes(),
                item.getStatus()
        );
    }
}
