package com.dtrps.modules.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** System settings page: general info, business rules, site-wide banner. */
public final class SettingsDtos {
  private SettingsDtos() {}

  // ===== Requests (also the values returned) =====

  public record GeneralSettings(
      @NotBlank(message = "Vui lòng nhập tên hệ thống")
          @Size(min = 2, max = 60, message = "Tên hệ thống dài 2–60 ký tự")
          String siteName,
      @NotBlank(message = "Vui lòng nhập email hỗ trợ")
          @Email(message = "Email hỗ trợ không hợp lệ")
          @Size(max = 120, message = "Email hỗ trợ tối đa 120 ký tự")
          String supportEmail,
      @NotBlank(message = "Vui lòng nhập hotline")
          @Size(max = 20, message = "Hotline tối đa 20 ký tự")
          String supportPhone,
      @Size(max = 60, message = "Giờ hỗ trợ tối đa 60 ký tự") String supportHours) {}

  public record RuleSettings(
      @NotNull(message = "Vui lòng nhập số ảnh tối đa")
          @Min(value = 1, message = "Số ảnh tối đa từ 1 đến 20")
          @Max(value = 20, message = "Số ảnh tối đa từ 1 đến 20")
          Integer maxPlaceImages,
      @NotNull(message = "Vui lòng nhập dung lượng ảnh tối đa")
          @Min(value = 1, message = "Dung lượng ảnh từ 1 đến 10 MB")
          @Max(value = 10, message = "Dung lượng ảnh từ 1 đến 10 MB")
          Integer maxImageMb,
      @NotNull(message = "Vui lòng nhập số ngày nhắc gia hạn")
          @Min(value = 1, message = "Nhắc gia hạn trước 1 đến 30 ngày")
          @Max(value = 30, message = "Nhắc gia hạn trước 1 đến 30 ngày")
          Integer renewalNoticeDays,
      @NotNull(message = "Vui lòng nhập thời hạn link thanh toán")
          @Min(value = 5, message = "Link thanh toán hết hạn sau 5 đến 60 phút")
          @Max(value = 60, message = "Link thanh toán hết hạn sau 5 đến 60 phút")
          Integer paymentLinkMinutes) {}

  public record AnnouncementSettings(
      boolean enabled,
      @Size(max = 300, message = "Nội dung thông báo tối đa 300 ký tự") String message,
      @NotNull(message = "Vui lòng chọn mức độ")
          @Pattern(regexp = "^(INFO|WARNING|CRITICAL)$", message = "Mức độ không hợp lệ")
          String level,
      @NotNull(message = "Vui lòng chọn đối tượng")
          @Pattern(regexp = "^(ALL|TRAVELLER|PARTNER)$", message = "Đối tượng không hợp lệ")
          String audience,
      Instant startsAt,
      Instant endsAt) {}

  // ===== Responses =====

  /** Who last saved a section and when (null when it still uses the defaults). */
  public record SectionMeta(Instant updatedAt, String updatedBy) {}

  public record SettingsView(
      GeneralSettings general,
      SectionMeta generalMeta,
      RuleSettings rules,
      SectionMeta rulesMeta,
      AnnouncementSettings announcement,
      SectionMeta announcementMeta,
      /* OFF, SCHEDULED, LIVE, ENDED */
      String announcementState) {}

  /** The banner as travellers / partners see it; {@code version} changes on every save. */
  public record PublicAnnouncement(
      String message, String level, String audience, Instant endsAt, long version) {}

  public record PublicLimits(int maxPlaceImages, int maxImageMb) {}

  /** GET /api/site-info: public, no sign-in. */
  public record SiteInfo(
      String siteName,
      String supportEmail,
      String supportPhone,
      String supportHours,
      PublicLimits limits,
      PublicAnnouncement announcement) {}
}
