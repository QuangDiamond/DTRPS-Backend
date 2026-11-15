package com.dtrps.modules.poi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Quản lý đánh giá của admin: đánh giá địa điểm (công khai, admin ẩn / hiện lại kèm lý do) và đánh
 * giá chuyến đi (riêng tư giữa khách và admin, chỉ xem).
 */
public final class AdminReviewDtos {
  private AdminReviewDtos() {}

  public record Author(Long userId, String name, String email, String avatarUrl) {}

  public record PlaceRef(Long placeId, String name) {}

  /** {@code hiddenReason}: lý do admin ẩn gần nhất, chỉ có khi đang HIDDEN. */
  public record PlaceReviewItem(
      Long reviewId,
      Author author,
      PlaceRef place,
      Short rating,
      String comment,
      List<String> imageUrls,
      String status,
      String hiddenReason,
      Instant createdAt,
      Instant updatedAt) {}

  public record PlanRef(
      Long planId, String title, String destination, LocalDate startDate, LocalDate endDate) {}

  public record TripFeedbackItem(
      Long feedbackId, Author author, PlanRef plan, Short rating, String comment, Instant createdAt) {}

  /** {@code lowRatingThisWeek}: đánh giá địa điểm 1-2 sao trong 7 ngày qua. */
  public record Stats(
      long placeTotal,
      long placeVisible,
      long placeHidden,
      Double placeAverage,
      long lowRatingThisWeek,
      long tripTotal,
      Double tripAverage,
      long reportedPending) {}

  /** Một lượt báo cáo: ai báo cáo, lý do (mã), ghi chú. */
  public record ReportEntry(
      Long reportId, Author reporter, String reason, String note, Instant createdAt) {}

  /**
   * Một đánh giá bị báo cáo, gộp mọi lượt báo cáo ở cùng trạng thái. {@code reasons}: mã lý do →
   * số lượt, nhiều nhất đứng đầu.
   */
  public record ReportedReviewItem(
      PlaceReviewItem review,
      String reportStatus,
      long reportCount,
      Instant latestReportAt,
      Map<String, Long> reasons,
      List<ReportEntry> reports) {}

  /**
   * Xử lý báo cáo: {@code HIDE} ẩn đánh giá (lý do bắt buộc, gửi tới người viết) hoặc {@code
   * DISMISS} bỏ qua vì không vi phạm (ghi chú nội bộ, không bắt buộc).
   */
  public record ResolveRequest(
      @NotBlank(message = "Vui lòng chọn cách xử lý")
          @Pattern(regexp = "^(HIDE|DISMISS)$", message = "Cách xử lý phải là HIDE hoặc DISMISS")
          String action,
      @Size(max = 1000, message = "Lý do tối đa 1000 ký tự") String reason) {}

  /** Ẩn / hiện lại luôn kèm lý do — lý do được gửi tới người viết đánh giá. */
  public record StatusRequest(
      @NotBlank(message = "Vui lòng chọn trạng thái")
          @Pattern(
              regexp = "^(VISIBLE|HIDDEN)$",
              message = "Trạng thái phải là VISIBLE hoặc HIDDEN")
          String status,
      @NotBlank(message = "Vui lòng nhập lý do")
          @Size(max = 1000, message = "Lý do tối đa 1000 ký tự")
          String reason) {}
}
