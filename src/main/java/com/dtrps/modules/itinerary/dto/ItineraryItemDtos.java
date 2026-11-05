
package com.dtrps.modules.itinerary.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public final class ItineraryItemDtos {

    private ItineraryItemDtos() {}

    // Request thêm hoạt động vào ngày
    public record CreateRequest(
            @NotNull
            @Positive
            Long placeId,

            LocalTime startTime,
            LocalTime endTime,

            @DecimalMin("0.00")
            @Digits(integer = 13, fraction = 2)
            BigDecimal estimatedCost,

            @Positive
            Long transportModeId,

            @PositiveOrZero
            Integer travelTimeMinutes,

            String notes
    ) {}

    // Request sửa thông tin hoạt động
    public record UpdateRequest(
            @Positive
            Long placeId,

            LocalTime startTime,
            LocalTime endTime,

            @DecimalMin("0.00")
            @Digits(integer = 13, fraction = 2)
            BigDecimal estimatedCost,

            @Positive
            Long transportModeId,

            @PositiveOrZero
            Integer travelTimeMinutes,

            String notes,
            String status
    ) {}

    // Request sắp xếp lại tất cả hoạt động trong ngày
    public record ReorderRequest(
            @NotNull
            List<@NotNull @Positive Long> itemIds
    ) {}

    // Response một hoạt động
    public record ItemResponse(
            Long itemId,
            Long dayId,
            Integer itemOrder,
            Long placeId,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal estimatedCost,
            Long transportModeId,
            String transportModeName,
            Integer travelTimeMinutes,
            String notes,
            String status
    ) {}
}
