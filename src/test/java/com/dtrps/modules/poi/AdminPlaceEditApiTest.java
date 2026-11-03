package com.dtrps.modules.poi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminPlaceEditApiTest extends ApiTestBase {
  private static final String PLACES = "/api/admin/places";

  private static Map<String, Object> basics(String name) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("categoryCode", "homestay");
    body.put("provinceCode", "hanoi");
    body.put("address", "1 Đinh Tiên Hoàng");
    body.put("latitude", 21.03);
    body.put("longitude", 105.85);
    return body;
  }

  private static List<Map<String, Object>> week() {
    List<Map<String, Object>> days = new ArrayList<>();
    for (int day = 1; day <= 7; day++) {
      days.add(
          day == 7
              ? Map.of("dayOfWeek", day, "closed", true)
              : Map.of(
                  "dayOfWeek", day, "openTime", "08:00", "closeTime", "17:00", "closed", false));
    }
    return days;
  }

  private JsonNode create(String admin) throws Exception {
    Map<String, Object> body = basics("POI admin " + UUID.randomUUID().toString().substring(0, 8));
    body.put("openingHours", week());
    body.put("prices", List.of(Map.of("priceType", "ticket", "amount", 30000)));
    body.put(
        "images",
        List.of(
            Map.of("url", "https://cdn.example.com/1.jpg", "primary", false),
            Map.of("url", "https://cdn.example.com/2.jpg", "primary", false)));
    return json(postAs(PLACES, admin, body).andExpect(status().isCreated()));
  }

  @Test
  @DisplayName("admin creates a public place directly, with hours, prices and photos")
  void create_isPublicAdminPlace() throws Exception {
    JsonNode place = create(adminToken());

    assertThat(place.get("summary").get("source").asText()).isEqualTo("ADMIN");
    assertThat(place.get("summary").get("status").asText()).isEqualTo("ACTIVE");
    assertThat(place.get("editable").asBoolean()).isTrue();
    assertThat(place.get("openingHours")).hasSize(7);
    assertThat(place.get("prices").get(0).get("currency").asText()).isEqualTo("VND");
    assertThat(place.get("images").get(0).get("primary").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("opening hours must cover all 7 days")
  void hours_needFullWeek() throws Exception {
    String admin = adminToken();
    Map<String, Object> body = basics("POI tuần thiếu");
    body.put("openingHours", week().subList(0, 3));
    postAs(PLACES, admin, body).andExpect(status().isBadRequest());

    String id = create(admin).get("summary").get("id").asText();
    putAs(PLACES + "/" + id + "/opening-hours", admin, Map.of("days", week().subList(0, 6)))
        .andExpect(status().isBadRequest());
    List<Map<String, Object>> allClosed = new ArrayList<>();
    for (int day = 1; day <= 7; day++) {
      allClosed.add(Map.of("dayOfWeek", day, "closed", true));
    }
    putAs(PLACES + "/" + id + "/opening-hours", admin, Map.of("days", allClosed))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openingHours.length()").value(7))
        .andExpect(jsonPath("$.openingHours[0].closed").value(true));
  }

  @Test
  @DisplayName("photos: https only, no duplicates, one cover moved within one transaction")
  void images_coverSwitches() throws Exception {
    String admin = adminToken();
    JsonNode place = create(admin);
    String id = place.get("summary").get("id").asText();
    String base = PLACES + "/" + id + "/images";

    postAs(base, admin, Map.of("url", "http://cdn.example.com/x.jpg"))
        .andExpect(status().isBadRequest());
    postAs(base, admin, Map.of("url", "https://cdn.example.com/1.jpg"))
        .andExpect(status().isConflict());

    JsonNode added =
        json(
            postAs(base, admin, Map.of("url", "https://cdn.example.com/3.jpg", "primary", true))
                .andExpect(status().isOk()));
    assertThat(added.get("images")).hasSize(3);
    assertThat(added.get("images").get(0).get("url").asText())
        .isEqualTo("https://cdn.example.com/3.jpg");

    String second = place.get("images").get(1).get("id").asText();
    putAs(base + "/" + second + "/primary", admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.images[0].id").value(Long.parseLong(second)))
        .andExpect(jsonPath("$.images[0].primary").value(true))
        .andExpect(jsonPath("$.images[1].primary").value(false))
        .andExpect(jsonPath("$.images[2].primary").value(false));

    deleteAs(base + "/" + second, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.images.length()").value(2))
        .andExpect(jsonPath("$.images[0].primary").value(true));
  }

  @Test
  @DisplayName("prices: add, edit, delete; amount cannot be negative")
  void prices_crud() throws Exception {
    String admin = adminToken();
    JsonNode place = create(admin);
    String id = place.get("summary").get("id").asText();
    String base = PLACES + "/" + id + "/prices";

    postAs(base, admin, Map.of("priceType", "TICKET", "amount", -1))
        .andExpect(status().isBadRequest());
    JsonNode added =
        json(
            postAs(base, admin, Map.of("priceType", "child", "amount", 15000))
                .andExpect(status().isOk()));
    String priceId = added.get("prices").get(1).get("id").asText();
    assertThat(added.get("prices").get(1).get("priceType").asText()).isEqualTo("CHILD");

    putAs(base + "/" + priceId, admin, Map.of("priceType", "CHILD", "amount", 20000))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prices[1].amount").value(20000));
    deleteAs(base + "/" + priceId, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prices.length()").value(1));
  }

  @Test
  @DisplayName("update keeps lists left out; status hides the place")
  void update_andStatus() throws Exception {
    String admin = adminToken();
    String id = create(admin).get("summary").get("id").asText();

    putAs(PLACES + "/" + id, admin, basics("Tên mới của POI"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.name").value("Tên mới của POI"))
        .andExpect(jsonPath("$.images.length()").value(2))
        .andExpect(jsonPath("$.openingHours.length()").value(7));
    putAs(PLACES + "/" + id + "/status", admin, Map.of("status", "PAUSED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.status").value("PAUSED"));
    putAs(PLACES + "/" + id + "/status", admin, Map.of("status", "GONE"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("partner places cannot be edited by the admin directly")
  void partnerPlace_isLocked() throws Exception {
    String token = travellerToken();
    String admin = adminToken();
    String taxCode =
        String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
    JsonNode profile =
        json(
            postAs(
                "/api/business-profile",
                token,
                Map.of(
                    "businessName", "Đối tác khoá",
                    "taxCode", taxCode,
                    "contactEmail", "lock@example.vn",
                    "contactPhone", "0912345678")));
    postAs("/api/admin/business-profiles/" + profile.get("id").asText() + "/approve", admin, null)
        .andExpect(status().isOk());
    String name = "Partner lock " + UUID.randomUUID().toString().substring(0, 8);
    postAs("/api/business-listings", token, basics(name)).andExpect(status().isCreated());
    String id =
        json(getAs(PLACES + "?keyword=" + name, admin)).get("items").get(0).get("id").asText();

    getAs(PLACES + "/" + id, admin).andExpect(jsonPath("$.editable").value(false));
    putAs(PLACES + "/" + id, admin, basics(name)).andExpect(status().isConflict());
    postAs(PLACES + "/" + id + "/images", admin, Map.of("url", "https://cdn.example.com/p.jpg"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("only admins can add places")
  void create_adminOnly() throws Exception {
    postAs(PLACES, travellerToken(), basics("Không được")).andExpect(status().isForbidden());
  }
}
