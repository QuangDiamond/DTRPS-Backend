package com.dtrps.modules.poi.dto;

import java.time.Instant;
import java.util.List;

/** Đánh giá một địa điểm: 1-5 sao + nhận xét + tối đa 5 ảnh, sửa/xóa được bởi chính khách. */
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
}
