
package com.dtrps.modules.itinerary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Request/Response cho chức năng bắt đầu chuyến đi theo từng ngày
 * và ghi nhận tiến độ từng địa điểm.
 *
 * Trạng thái được lưu ở ba bảng:
 * trip_progress, trip_day_progress, activity_progress.
 */
public final class TripProgressDtos {

    private TripProgressDtos() {}

    /**
     * Request đánh dấu hoạt động đã hoàn thành hoặc bỏ qua.
     *
     * Bắt đầu và hoàn thành ngày không cần request body.
     * Service xác định ngày từ dayId và userId từ JWT.
     */
    public record UpdateActivityRequest(
            @NotBlank(
                    message = "Trạng thái hoạt động không được để trống"
            )
            @Pattern(
                    regexp = "COMPLETED|SKIPPED",
                    message = "Trạng thái hoạt động phải là COMPLETED hoặc SKIPPED"
            )
            String status,

            String notes
    ) {}

    /**
     * Tiến độ của một hoạt động trong ngày.
     *
     * Nếu chưa có ActivityProgress,
     * Service sẽ trả về status = PENDING.
     */
    public record ActivityResponse(
            Long activityProgressId,
            Long itemId,
            Integer itemOrder,
            Long placeId,
            String placeName,
            String status,
            Instant completedAt,
            String notes
    ) {}

    /**
     * Tiến độ của một ngày trong lịch trình.
     *
     * visitDate được tính từ ngày bắt đầu TravelPlan
     * và dayNumber vì ItineraryDay không có visitDate.
     */
    public record DayResponse(
            Long dayProgressId,
            Long dayId,
            Integer dayNumber,
            LocalDate visitDate,
            String title,
            String status,
            Instant startedAt,
            Instant completedAt,
            List<ActivityResponse> activities
    ) {}

    /**
     * Tiến độ toàn bộ lịch trình,
     * bao gồm các ngày và hoạt động bên trong.
     *
     * progressId có thể null nếu chuyến đi chưa bắt đầu.
     */
    public record ProgressResponse(
            Long progressId,
            Long planId,
            Long itineraryId,
            String status,
            Instant startedAt,
            Instant completedAt,
            List<DayResponse> days
    ) {}
}
