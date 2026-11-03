package com.dtrps.modules.user;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminUserListApiTest extends ApiTestBase {
  private static final String USERS = "/api/admin/users";

  @Test
  @DisplayName("filters by keyword, role and status; newest first with created date")
  void list_filters() throws Exception {
    String email = uniqueEmail();
    session(email);
    String admin = adminToken();

    getAs(USERS + "?keyword=" + email, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].email").value(email))
        .andExpect(jsonPath("$.items[0].roles[0]").value("TRAVELLER"))
        .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty());
    getAs(USERS + "?keyword=" + email + "&role=ADMIN", admin)
        .andExpect(jsonPath("$.total").value(0));
    getAs(USERS + "?keyword=" + email + "&status=SUSPENDED", admin)
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  @DisplayName("a lock reason is kept and shown to admins until the account is unlocked")
  void lock_withReason() throws Exception {
    String email = uniqueEmail();
    String userId = session(email).get("user").get("id").asText();
    String admin = adminToken();

    Map<String, Object> lock = new HashMap<>();
    lock.put("status", "SUSPENDED");
    lock.put("reason", "Spam đánh giá");
    putAs(USERS + "/" + userId + "/status", admin, lock)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusNote").value("Spam đánh giá"));
    getAs(USERS + "/" + userId, admin).andExpect(jsonPath("$.statusNote").value("Spam đánh giá"));
    getAs(USERS + "?keyword=" + email + "&status=SUSPENDED", admin)
        .andExpect(jsonPath("$.items[0].statusNote").value("Spam đánh giá"));

    putAs(USERS + "/" + userId + "/status", admin, Map.of("status", "ACTIVE"))
        .andExpect(status().isOk());
    getAs(USERS + "/" + userId, admin).andExpect(jsonPath("$.statusNote").isEmpty());
  }

  @Test
  @DisplayName("stats count accounts by status and role")
  void stats() throws Exception {
    session(uniqueEmail());
    getAs(USERS + "/stats", adminToken())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").isNumber())
        .andExpect(jsonPath("$.travellers").isNumber())
        .andExpect(jsonPath("$.admins").value(1))
        .andExpect(jsonPath("$.newThisMonth").isNumber());
  }
}
