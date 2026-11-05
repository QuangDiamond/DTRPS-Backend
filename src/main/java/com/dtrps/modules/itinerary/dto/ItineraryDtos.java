package com.dtrps.modules.itinerary.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class ItineraryDtos {

    private ItineraryDtos() {}

    // Request tạo lịch trình mới
    public record CreateRequest(
            @Size(max = 255)
            String title,

            String notes
    ) {}

    // Dữ liệu một ngày trong lịch trình
    public record DayResponse(
            Long dayId,
            Integer dayNumber,
            LocalDate visitDate,
            String title,
            String notes
    ) {}

    // Response toàn bộ lịch trình
    public record ItineraryResponse(
            Long itineraryId,
            Long planId,
            String title,
            Integer versionNumber,
            String status,
            String currency,
            LocalDate startDate,
            LocalDate endDate,
            List<DayResponse> days
    ) {}
}