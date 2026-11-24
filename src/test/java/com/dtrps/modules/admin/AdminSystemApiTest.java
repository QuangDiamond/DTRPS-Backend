package com.dtrps.modules.admin;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Integration tests for Manage System Settings & View Audit Logs (Report 5.2). */
class AdminSystemApiTest extends ApiTestBase {

  @Test
  @DisplayName("admin reads and updates general settings and rules; non-admin is forbidden")
  void settings_crudAndAccessControl() throws Exception {
    String admin = adminToken();

    getAs("/api/admin/settings", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.general.siteName").isNotEmpty())
        .andExpect(jsonPath("$.rules.maxPlaceImages").isNumber());

    putAs(
            "/api/admin/settings/general",
            admin,
            Map.of(
                "siteName", "SmartTravel VN",
                "supportEmail", "support@smarttravel.vn",
                "supportPhone", "0912345678"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.general.siteName").value("SmartTravel VN"));

    putAs(
            "/api/admin/settings/rules",
            admin,
            Map.of(
                "maxPlaceImages", 12,
                "maxImageMb", 5,
                "renewalNoticeDays", 7,
                "paymentLinkMinutes", 15))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rules.maxPlaceImages").value(12));

    getAs("/api/admin/integrations", admin).andExpect(status().isOk());
    // OPENAI only supports config check → controlled 400
    postAs("/api/admin/integrations/OPENAI/check", admin, null).andExpect(status().isBadRequest());

    // Non-admin cannot access or modify system settings
    String traveller = travellerToken();
    getAs("/api/admin/settings", traveller).andExpect(status().isForbidden());
    putAs(
            "/api/admin/settings/general",
            traveller,
            Map.of(
                "siteName", "Hack",
                "supportEmail", "hack@test.vn",
                "supportPhone", "0912345678"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("admin views and filters audit logs; non-admin is forbidden")
  void auditLogs_listFilterAndAccessControl() throws Exception {
    String admin = adminToken();

    // Ensure at least one audit entry exists
    putAs(
            "/api/admin/settings/general",
            admin,
            Map.of(
                "siteName", "SmartTravel Audit",
                "supportEmail", "audit@smarttravel.vn",
                "supportPhone", "0912345678"))
        .andExpect(status().isOk());

    getAs("/api/admin/audit-logs?action=SETTINGS_UPDATED", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].action").value("SETTINGS_UPDATED"));

    getAs("/api/admin/audit-logs/filters", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actions").isArray());

    // Invalid date range → 400
    getAs("/api/admin/audit-logs?from=2026-11-30&to=2026-11-01", admin)
        .andExpect(status().isBadRequest());

    // Non-admin forbidden
    getAs("/api/admin/audit-logs", travellerToken()).andExpect(status().isForbidden());
  }
}
