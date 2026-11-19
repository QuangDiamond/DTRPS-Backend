package com.dtrps.modules.admin.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.admin.dto.AuditDtos.AuditFilters;
import com.dtrps.modules.admin.dto.AuditDtos.AuditLogItem;
import com.dtrps.modules.admin.dto.IntegrationDtos.IntegrationStatus;
import com.dtrps.modules.admin.dto.SettingsDtos.AnnouncementSettings;
import com.dtrps.modules.admin.dto.SettingsDtos.GeneralSettings;
import com.dtrps.modules.admin.dto.SettingsDtos.RuleSettings;
import com.dtrps.modules.admin.dto.SettingsDtos.SettingsView;
import com.dtrps.modules.admin.service.AuditLogQueryService;
import com.dtrps.modules.admin.service.IntegrationHealthService;
import com.dtrps.modules.admin.service.SystemSettingsService;
import com.dtrps.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /admin/settings: system settings, activity log, external service health. */
@RestController
@RequestMapping("/api/admin")
@Validated
@Tag(name = "Admin - System", description = "Cài đặt hệ thống, nhật ký, tình trạng dịch vụ")
public class AdminSystemController {
  private static final int MAX_PAGE_SIZE = 100;

  private final SystemSettingsService settings;
  private final AuditLogQueryService auditLogs;
  private final IntegrationHealthService integrations;

  public AdminSystemController(
      SystemSettingsService settings,
      AuditLogQueryService auditLogs,
      IntegrationHealthService integrations) {
    this.settings = settings;
    this.auditLogs = auditLogs;
    this.integrations = integrations;
  }

  // ===== Settings =====

  @GetMapping("/settings")
  @Operation(summary = "Toàn bộ cài đặt hệ thống")
  public SettingsView settings() {
    return settings.view();
  }

  @PutMapping("/settings/general")
  @Operation(summary = "Lưu thông tin chung")
  public SettingsView saveGeneral(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GeneralSettings request) {
    return settings.saveGeneral(CurrentUser.id(jwt), request);
  }

  @PutMapping("/settings/rules")
  @Operation(summary = "Lưu quy tắc nghiệp vụ")
  public SettingsView saveRules(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RuleSettings request) {
    return settings.saveRules(CurrentUser.id(jwt), request);
  }

  @PutMapping("/settings/announcement")
  @Operation(summary = "Lưu thông báo toàn hệ thống (banner)")
  public SettingsView saveAnnouncement(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AnnouncementSettings request) {
    return settings.saveAnnouncement(CurrentUser.id(jwt), request);
  }

  // ===== Activity log =====

  @GetMapping("/audit-logs")
  @Operation(summary = "Nhật ký thao tác của quản trị viên, mới nhất trước")
  public PageResponse<AuditLogItem> auditLogs(
      @RequestParam(required = false) @Size(max = 100, message = "Từ khoá tối đa 100 ký tự")
          String keyword,
      @RequestParam(required = false)
          @Pattern(regexp = "^[A-Z0-9_]{1,100}$", message = "Loại thao tác không hợp lệ")
          String action,
      @RequestParam(required = false) @Min(1) Long actorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return auditLogs.search(
        keyword,
        action,
        actorId,
        from,
        to,
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "logId")));
  }

  @GetMapping("/audit-logs/filters")
  @Operation(summary = "Các loại thao tác và người thao tác có trong nhật ký")
  public AuditFilters auditFilters() {
    return auditLogs.filters();
  }

  // ===== External services =====

  @GetMapping("/integrations")
  @Operation(summary = "Tình trạng dịch vụ ngoài (không gọi mạng)")
  public List<IntegrationStatus> integrations() {
    return integrations.list();
  }

  @PostMapping("/integrations/{key}/check")
  @Operation(summary = "Kiểm tra kết nối thật tới một dịch vụ")
  public IntegrationStatus check(
      @PathVariable @Pattern(regexp = "^[A-Z]{2,20}$", message = "Dịch vụ không hợp lệ")
          String key) {
    return integrations.check(key);
  }
}
