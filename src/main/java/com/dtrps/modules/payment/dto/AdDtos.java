package com.dtrps.modules.payment.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Gói quảng cáo của đối tác: bảng giá, đăng ký, thanh toán PayOS và địa điểm được quảng cáo. */
public final class AdDtos {
  private AdDtos() {}

  public record PackageItem(
      Long id,
      String name,
      String description,
      BigDecimal price,
      Integer durationDays,
      Integer maxPlaces,
      String status,
      Integer sortOrder) {}

  public record SubscriptionInfo(
      Long id,
      String packageName,
      BigDecimal price,
      Integer maxPlaces,
      Integer durationDays,
      String status,
      Instant startDate,
      Instant endDate,
      long daysLeft,
      long usedSlots) {}

  /** Phiếu sự cố mới nhất của đơn: OPEN, RESOLVED hoặc REJECTED */
  public record PaymentIssueInfo(Long id, String status, String resolutionNote) {}

  public record PaymentItem(
      Long id,
      String orderCode,
      String packageName,
      BigDecimal amount,
      String currency,
      String type,
      String status,
      String checkoutUrl,
      Instant linkExpiresAt,
      Instant paidAt,
      Instant createdAt,
      PaymentIssueInfo issue) {}

  /** Đối tác báo đã chuyển tiền mà đơn chưa được ghi nhận; biên lai tải lên qua /uploads/images */
  public record ReportIssueRequest(
      @NotNull(message = "Vui lòng nhập số tiền đã chuyển")
          @Digits(integer = 12, fraction = 0, message = "Số tiền phải là số nguyên")
          @Min(value = 1, message = "Số tiền phải lớn hơn 0")
          BigDecimal transferredAmount,
      @NotNull(message = "Vui lòng nhập thời gian chuyển khoản")
          @PastOrPresent(message = "Thời gian chuyển khoản không được ở tương lai")
          Instant transferredAt,
      @NotBlank(message = "Vui lòng nhập mã giao dịch ngân hàng")
          @Size(max = 100, message = "Mã giao dịch tối đa 100 ký tự")
          String bankReference,
      @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự") String note,
      @NotBlank(message = "Vui lòng tải lên ảnh biên lai")
          @Size(max = 1000, message = "Đường dẫn biên lai quá dài")
          @Pattern(
              regexp = "^https://res\\.cloudinary\\.com/.+",
              message = "Ảnh biên lai không hợp lệ")
          String receiptUrl) {}

  /**
   * {@code renewable}: đang trong N ngày cuối (Cài đặt hệ thống → rules.renewal_notice_days) và cửa
   * sổ gia hạn đang mở
   */
  public record PartnerState(
      SubscriptionInfo subscription,
      PaymentItem pendingPayment,
      boolean renewable,
      long eligiblePlaces) {}

  public record CheckoutRequest(@NotNull(message = "Vui lòng chọn gói") Long packageId) {}

  public record CheckoutResponse(String orderCode, String checkoutUrl) {}

  /** {@code ineligibleReason}: vì sao địa điểm chưa chọn được (null khi đủ điều kiện) */
  public record SlotPlace(
      Long listingId,
      Long placeId,
      String name,
      String address,
      String province,
      String coverUrl,
      boolean eligible,
      String ineligibleReason,
      boolean selected,
      boolean suspended,
      String suspendReason) {}

  public record SlotsResponse(Integer maxPlaces, List<SlotPlace> places) {}

  public record SaveSlotsRequest(
      @NotNull(message = "Vui lòng chọn địa điểm") List<@NotNull Long> listingIds) {}

  public record FeaturedPlace(
      Long id,
      String name,
      String category,
      String province,
      String coverUrl,
      Double rating,
      long reviewCount) {}

  // ===== Admin =====

  /**
   * Các trường số dùng BigDecimal: nếu khai báo Integer / Long, Jackson tự cắt 1.1 thành 1 mà không
   * báo lỗi.
   */
  public record PackageRequest(
      @NotBlank(message = "Vui lòng nhập tên gói")
          @Size(max = 150, message = "Tên gói tối đa 150 ký tự")
          String name,
      @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự") String description,
      @NotNull(message = "Vui lòng nhập giá")
          @Digits(integer = 10, fraction = 0, message = "Giá phải là số nguyên")
          @Min(value = 1000, message = "Giá tối thiểu 1.000đ")
          @Max(value = 1000000000, message = "Giá tối đa 1.000.000.000đ")
          BigDecimal price,
      @NotNull(message = "Vui lòng nhập số ngày")
          @Digits(integer = 4, fraction = 0, message = "Số ngày phải là số nguyên")
          @Min(value = 1, message = "Số ngày phải từ 1 đến 3650")
          @Max(value = 3650, message = "Số ngày phải từ 1 đến 3650")
          BigDecimal durationDays,
      @NotNull(message = "Vui lòng nhập số địa điểm")
          @Digits(integer = 3, fraction = 0, message = "Số địa điểm phải là số nguyên")
          @Min(value = 1, message = "Số địa điểm phải từ 1 đến 100")
          @Max(value = 100, message = "Số địa điểm phải từ 1 đến 100")
          BigDecimal maxPlaces,
      @NotNull(message = "Vui lòng nhập thứ tự")
          @Digits(integer = 4, fraction = 0, message = "Thứ tự phải là số nguyên")
          @Min(value = 1, message = "Thứ tự phải từ 1 đến 9999")
          @Max(value = 9999, message = "Thứ tự phải từ 1 đến 9999")
          BigDecimal sortOrder) {}

  public record PackageStatusRequest(
      @NotBlank @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "Trạng thái gói không hợp lệ")
          String status) {}

  public record PaymentStats(
      BigDecimal revenueThisMonth, long success, long pending, long needsReview, long openIssues) {}

  public record AdminPaymentIssue(
      Long id,
      Long paymentId,
      String orderCode,
      String paymentStatus,
      Long businessId,
      String businessName,
      String packageName,
      BigDecimal amount,
      BigDecimal transferredAmount,
      Instant transferredAt,
      String bankReference,
      String note,
      String receiptUrl,
      String status,
      String resolutionNote,
      Instant handledAt,
      Instant createdAt) {}

  /** Chỉ kích hoạt tay khi đã thấy tiền trong sao kê ngân hàng */
  public record ManualActivateRequest(
      @NotBlank(message = "Vui lòng nhập mã giao dịch ngân hàng")
          @Size(max = 255, message = "Mã giao dịch tối đa 255 ký tự")
          String bankReference,
      @NotBlank(message = "Vui lòng nhập lý do") @Size(max = 1000) String reason) {}

  public record AdminPayment(
      Long id,
      String orderCode,
      Long businessId,
      String businessName,
      String packageName,
      BigDecimal amount,
      String type,
      String status,
      String gatewayStatus,
      String bankReference,
      boolean needsReview,
      String reviewNote,
      String refundReference,
      String refundReason,
      Instant refundedAt,
      Instant paidAt,
      Instant createdAt) {}

  public record RefundRequest(
      @NotBlank(message = "Vui lòng nhập lý do hoàn tiền") @Size(max = 1000) String reason,
      @NotBlank(message = "Vui lòng nhập mã chuyển khoản hoàn tiền") @Size(max = 255)
          String reference) {}

  public record AdminSubscription(
      Long id,
      Long businessId,
      String businessName,
      String packageName,
      BigDecimal price,
      Integer maxPlaces,
      long usedSlots,
      String status,
      Instant startDate,
      Instant endDate,
      long daysLeft,
      String cancelReason,
      /* Có khi gói được admin kích hoạt tay từ phiếu sự cố (PayOS không ghi nhận); null nếu không */
      ManualActivation manualActivation) {}

  /** Lần kích hoạt tay gần nhất của một gói: đơn, mã giao dịch ngân hàng, phiếu sự cố đã xử lý. */
  public record ManualActivation(
      Long paymentId,
      String orderCode,
      String bankReference,
      Long issueId,
      String reason,
      String handledBy,
      Instant activatedAt) {}

  public record ReasonRequest(
      @NotBlank(message = "Vui lòng nhập lý do") @Size(max = 1000) String reason) {}

  public record AdminAdvertisement(
      Long id,
      Long placeId,
      String placeName,
      Long businessId,
      String businessName,
      String packageName,
      String status,
      String suspendReason,
      Instant startAt,
      Instant endAt) {}

  public record AdStatusRequest(
      @NotBlank
          @Pattern(
              regexp = "^(PUBLISHED|SUSPENDED)$",
              message = "Trạng thái quảng cáo không hợp lệ")
          String status,
      @NotBlank(message = "Vui lòng nhập lý do") @Size(max = 1000) String reason) {}
}
