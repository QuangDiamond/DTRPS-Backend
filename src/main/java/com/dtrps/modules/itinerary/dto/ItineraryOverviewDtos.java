
package com.dtrps.modules.itinerary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class ItineraryOverviewDtos {

    private ItineraryOverviewDtos() {}

    // Thông tin một hoạt động trong ngày
    public record ActivityResponse(
            Long itemId,
            Integer itemOrder,
            Long placeId,
            String placeName,
            String placeAddress,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal estimatedCost,
            Long transportModeId,
            String transportModeName,
            Integer travelTimeMinutes,
            String notes,
            String status
    ) {}

    // Thông tin ngày và tổng chi phí của ngày
    public record DayResponse(
            Long dayId,
            Integer dayNumber,
            LocalDate visitDate,
            String title,
            String notes,
            BigDecimal estimatedCost,
            List<ActivityResponse> activities
    ) {}

    // Thông tin lịch trình và tổng chi phí chuyến đi
    public record OverviewResponse(
            Long itineraryId,
            Long planId,
            String planTitle,
            String destination,
            String itineraryTitle,
            Integer versionNumber,
            String generationSource,
            String status,
            String currency,
            String itineraryNotes,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal budget,
            BigDecimal totalEstimatedCost,
            List<DayResponse> days
    ) {}
}
