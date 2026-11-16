package com.dtrps.modules.poi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Đánh giá một địa điểm: 1-5 sao + nhận xét + tối đa 5 ảnh. Gửi một lần, không sửa/xóa lại được. */
public final class PlaceReviewDtos {
  private PlaceReviewDtos() {}

  public record ReviewResponse(
      Long reviewId,
      Long placeId,
      Long userId,
      String userName,
      String userAvatarUrl,
      Short rating,
      String comment,
      List<String> imageUrls,
      Instant createdAt,
      Instant updatedAt) {}

  /**
   * {@code canReview}: true nếu khách đã đi địa điểm này (COMPLETED) nhiều lần hơn số đánh giá đã
   * gửi — mỗi lần đi (kể cả đi lại) được đánh giá thêm một lần. Chưa từng đi, hoặc đã đánh giá đủ
   * số lần đã đi, thì {@code canReview = false} — chỉ xem được đánh giá, không viết thêm được.
   */
  public record ReviewStatusResponse(boolean canReview) {}

  /** Báo cáo đánh giá của người khác. {@code OTHER} cần ghi chú. */
  public record ReportRequest(
      @NotBlank(message = "Vui lòng chọn lý do báo cáo")
          @Pattern(
              regexp = "^(SPAM|OFFENSIVE|INAPPROPRIATE|MISLEADING|FAKE|PRIVACY|OTHER)$",
              message = "Lý do báo cáo không hợp lệ")
          String reason,
      @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {}
}
