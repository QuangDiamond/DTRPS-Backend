
package com.dtrps.modules.itinerary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * DTO tra ve lich trinh, tung ngay va hoat dong
 * de hien thi tren Web va Goong Map.
 */
public final class ItineraryOverviewDtos {

    private ItineraryOverviewDtos() {}

    /**
     * Thong tin mot hoat dong trong ngay.
     *
     * latitude/longitude la toa do cua dia diem,
     * khong phai vi tri GPS cua Traveller.
     *
     * Co the null neu Place chua co toa do.
     */
    public record ActivityResponse(
            Long itemId,
            Integer itemOrder,
            Long placeId,
            String placeName,
            String placeAddress,
            Double latitude,
            Double longitude,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal estimatedCost,
            Long transportModeId,
            String transportModeName,
            Integer travelTimeMinutes,
            String notes,
            String status
    ) {

        /**
         * Constructor tuong thich voi Service hien tai.
         *
         * Buoc 14 se cap nhat Service de truyen
         * latitude va longitude that tu Place.
         */
        public ActivityResponse(
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
        ) {
            this(
                    itemId,
                    itemOrder,
                    placeId,
                    placeName,
                    placeAddress,
                    null,
                    null,
                    startTime,
                    endTime,
                    estimatedCost,
                    transportModeId,
                    transportModeName,
                    travelTimeMinutes,
                    notes,
                    status
            );
        }
    }

    /**
     * Thong tin ngay va tong chi phi cua ngay.
     */
    public record DayResponse(
            Long dayId,
            Integer dayNumber,
            LocalDate visitDate,
            String title,
            String notes,
            BigDecimal estimatedCost,
            List<ActivityResponse> activities
    ) {}

    /**
     * Thong tin lich trinh va tong chi phi chuyen di.
     */
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
