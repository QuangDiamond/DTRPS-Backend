package com.dtrps.modules.poi;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminCategoryApiTest extends ApiTestBase {
  private static final String CATEGORIES = "/api/admin/place-categories";

  @Autowired private JdbcTemplate jdbcTemplate;

  /** Other tests count the seeded types, so the ones created here are removed again */
  @AfterEach
  void removeCreatedTypes() {
    jdbcTemplate.update("DELETE FROM place_categories WHERE code LIKE 'cave\\_%'");
  }

  private static Map<String, Object> type(String code, String name) {
    Map<String, Object> body = new HashMap<>();
    body.put("groupCode", "attraction");
    body.put("code", code);
    body.put("name", name);
    return body;
  }

  @Test
  @DisplayName("lists the 4 groups with their types and usage")
  void list_groups() throws Exception {
    getAs(CATEGORIES, adminToken())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code == 'lodging')].types[*].code").isNotEmpty())
        .andExpect(jsonPath("$[0].types[0].placeCount").isNumber());
  }

  @Test
  @DisplayName("add, rename and switch off a type; code and name must be unique")
  void crud_andUniqueness() throws Exception {
    String admin = adminToken();
    String suffix = UUID.randomUUID().toString().substring(0, 6);
    String code = "cave_" + suffix;
    String name = "Hang động " + suffix;

    String id =
        json(postAs(CATEGORIES, admin, type(code, name))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE")))
            .get("id")
            .asText();
    postAs(CATEGORIES, admin, type(code, "Tên khác " + suffix)).andExpect(status().isConflict());
    postAs(CATEGORIES, admin, type("other_" + suffix, name)).andExpect(status().isConflict());
    postAs(CATEGORIES, admin, type("Có Dấu", "Sai mã " + suffix))
        .andExpect(status().isBadRequest());

    putAs(CATEGORIES + "/" + id, admin, type(null, "Hang động đẹp " + suffix))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.name").value("Hang động đẹp " + suffix));

    putAs(CATEGORIES + "/" + id + "/status", admin, Map.of("status", "INACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
    // An inactive type is gone from place forms
    getAs("/api/reference/categories", admin)
        .andExpect(jsonPath("$[*].subtypes[?(@.code == '" + code + "')]").isEmpty());
  }

  @Test
  @DisplayName("only admins manage types")
  void adminOnly() throws Exception {
    getAs(CATEGORIES, travellerToken()).andExpect(status().isForbidden());
  }
}
