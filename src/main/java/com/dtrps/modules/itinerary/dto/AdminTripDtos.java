package com.dtrps.modules.itinerary.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Quản lý chuyến đi của admin (chỉ xem): danh sách, tiến độ và chi tiết từng chuyến. */
public final class AdminTripDtos {
  private AdminTripDtos() {}

  public record Traveller(Long userId, String name, String email, String avatarUrl) {}

  /**
   * Tiến độ thực tế. {@code currentDay}: ngày đang đi, hoặc ngày kế tiếp khi chưa bấm bắt đầu
   * ({@code currentDayStarted = false}); null nếu chuyến không ở trạng thái đang đi.
   */
  public record Progress(
      int totalDays,
      int completedDays,
      Integer currentDay,
      boolean currentDayStarted,
      int totalStops,
      int completedStops,
      int skippedStops,
      int percent) {}

  /** {@code overdue}: đang đi nhưng đã qua ngày về dự kiến. */
  public record TripSummary(
      Long planId,
      String title,
      String destination,
      LocalDate startDate,
      LocalDate endDate,
      Integer groupSize,
      BigDecimal budget,
      String currency,
      String status,
      boolean overdue,
      Traveller traveller,
      Progress progress,
      Instant startedAt,
      Instant completedAt,
      Instant createdAt) {}

  public record Stats(
      long total, long planning, long inProgress, long overdue, long completed, long cancelled) {}

  /** {@code status}: PENDING, COMPLETED hoặc SKIPPED. */
  public record Stop(
      Long itemId,
      Integer order,
      Long placeId,
      String placeName,
      String address,
      String coverUrl,
      LocalTime startTime,
      LocalTime endTime,
      BigDecimal estimatedCost,
      String status,
      Instant completedAt,
      String notes) {}

  /** {@code status}: NOT_STARTED, IN_PROGRESS hoặc COMPLETED. */
  public record Day(
      Integer dayNumber,
      LocalDate date,
      String title,
      String status,
      Instant startedAt,
      Instant completedAt,
      List<Stop> stops) {}

  public record SelectedPlace(Long placeId, String name, String address, String coverUrl) {}

  public record Feedback(Short rating, String comment, Instant createdAt) {}

  /** {@code selectedPlaces}: địa điểm khách đã chọn, dùng khi chuyến chưa có lịch trình. */
  public record TripDetail(
      TripSummary summary,
      String description,
      String travelPace,
      String itineraryTitle,
      Integer itineraryVersion,
      List<Day> days,
      List<SelectedPlace> selectedPlaces,
      Feedback feedback) {}
}
