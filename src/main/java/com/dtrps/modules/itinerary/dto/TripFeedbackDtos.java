package com.dtrps.modules.itinerary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Đánh giá 1-5 sao + nhận xét sau chuyến đi. */
public final class TripFeedbackDtos {
  private TripFeedbackDtos() {}

  public record CreateRequest(
      @NotNull(message = "Vui lòng chọn số sao đánh giá")
          @Min(value = 1, message = "Đánh giá từ 1 đến 5 sao")
          @Max(value = 5, message = "Đánh giá từ 1 đến 5 sao")
          Short rating,
      @Size(max = 2000, message = "Nhận xét tối đa 2000 ký tự") String comment) {}

  public record FeedbackResponse(
      Long feedbackId, Long planId, Short rating, String comment, Instant createdAt) {}
}
