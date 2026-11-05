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

  /** Các trường chung của tạo và sửa kế hoạch, để service dùng một hàm gán dữ liệu. */
  public interface PlanFields {
    String title();

    String destination();

    LocalDate startDate();

    LocalDate endDate();

    BigDecimal budget();

    String currency();

    Integer groupSize();

    String description();

    List<String> categoryCodes();

    List<String> interestCodes();

    String travelPace();
  }

  public record CreateRequest(
      @NotBlank(message = "Tên kế hoạch không được để trống") @Size(max = 255) String title,
      @NotBlank(message = "Điểm đến không được để trống") @Size(max = 255) String destination,
      @NotNull(message = "Ngày bắt đầu không được để trống") LocalDate startDate,
      @NotNull(message = "Ngày kết thúc không được để trống") LocalDate endDate,
      @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal budget,
      @Pattern(regexp = "[A-Za-z]{3}") String currency,
      @Positive Integer groupSize,
      String description,

      // Mã loại địa điểm (place_categories), dùng cho gợi ý địa điểm
      @Size(max = 50) List<@NotBlank @Size(max = 50) String> categoryCodes,

      // Mã sở thích của chuyến (bảng interests), lưu vào plan_interests
      @Size(max = 50) List<@NotBlank @Size(max = 50) String> interestCodes,
      @Pattern(regexp = "RELAXED|BALANCED|PACKED", message = "Nhịp độ không hợp lệ")
          String travelPace)
      implements PlanFields {}

  /** Sửa toàn bộ kế hoạch (PUT): gửi đủ các trường như khi tạo. */
  public record UpdateRequest(
      @NotBlank(message = "Tên kế hoạch không được để trống") @Size(max = 255) String title,
      @NotBlank(message = "Điểm đến không được để trống") @Size(max = 255) String destination,
      @NotNull(message = "Ngày bắt đầu không được để trống") LocalDate startDate,
      @NotNull(message = "Ngày kết thúc không được để trống") LocalDate endDate,
      @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal budget,
      @Pattern(regexp = "[A-Za-z]{3}") String currency,
      @Positive Integer groupSize,
      String description,
      @Size(max = 50) List<@NotBlank @Size(max = 50) String> categoryCodes,
      @Size(max = 50) List<@NotBlank @Size(max = 50) String> interestCodes,
      @Pattern(regexp = "RELAXED|BALANCED|PACKED", message = "Nhịp độ không hợp lệ")
          String travelPace)
      implements PlanFields {}

  public record ChangeStatusRequest(
      @NotBlank(message = "Trạng thái không được để trống")
          @Pattern(
              regexp = "DRAFT|PLANNED|IN_PROGRESS|COMPLETED|CANCELLED",
              message = "Trạng thái không hợp lệ")
          String status) {}

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
      List<String> categoryCodes,
      List<String> interestCodes,
      String travelPace,
      String status,

      // Các trạng thái được phép chuyển tới, để client hiện đúng nút
      List<String> allowedNextStatuses,
      Instant createdAt,
      Instant updatedAt) {}
}
