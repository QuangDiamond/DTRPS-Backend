package com.dtrps.modules.admin.controller;

import com.dtrps.modules.admin.dto.SettingsDtos.SiteInfo;
import com.dtrps.modules.admin.service.SystemSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: support contact, upload limits and the live site-wide banner. */
@RestController
@Tag(name = "Site info", description = "Thông tin công khai của hệ thống")
public class SiteInfoController {
  private final SystemSettingsService settings;

  public SiteInfoController(SystemSettingsService settings) {
    this.settings = settings;
  }

  @GetMapping("/api/site-info")
  @Operation(summary = "Liên hệ hỗ trợ, giới hạn tải ảnh, thông báo đang hiện")
  public SiteInfo siteInfo() {
    return settings.siteInfo();
  }
}
