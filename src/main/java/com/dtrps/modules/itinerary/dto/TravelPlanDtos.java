
package com.dtrps.modules.itinerary.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TravelPlanDtos {

    private TravelPlanDtos() {}

    public record CreateRequest(

            @NotBlank(message = "Tên kế hoạch không được để trống")
            @Size(max = 255)
            String title,

            @NotBlank(message = "Điểm đến không được để trống")
            @Size(max = 255)
            String destination,

            @NotNull(message = "Ngày bắt đầu không được để trống")
            LocalDate startDate,

            @NotNull(message = "Ngày kết thúc không được để trống")
            LocalDate endDate,

            @DecimalMin("0.00")
            @Digits(integer = 13, fraction = 2)
            BigDecimal budget,

            @Pattern(regexp = "[A-Za-z]{3}")
            String currency,

            @Positive
            Integer groupSize,

            String description,

            // Mã danh mục trải nghiệm lấy từ Backend
            @Size(max = 50)
            List<
                    @NotBlank
                    @Size(max = 50)
                            String
                    > categoryCodes,

            @Pattern(regexp = "RELAXED|BALANCED|PACKED", message = "Nhịp độ không hợp lệ")
            String travelPace
    ) {}

    public record PlanResponse(

            Long planId,
            Long userId,
            String title,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal budget,
            String currency,
            Integer groupSize,
            String description,

            // Trả về những sở thích đã chọn
            List<String> categoryCodes,
            String travelPace,

            String status,
            Instant createdAt,
            Instant updatedAt
    ) {}
}