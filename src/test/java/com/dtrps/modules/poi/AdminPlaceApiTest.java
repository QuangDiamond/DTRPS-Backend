package com.dtrps.modules.poi;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminPlaceApiTest extends ApiTestBase {
  private static final String PLACES = "/api/admin/places";

  /** A partner place with two photos; returns its unique name. */
  private String partnerPlace(boolean approve) throws Exception {
    String name = "Kho POI " + UUID.randomUUID().toString().substring(0, 8);
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
                    "businessName", "Nhà Gỗ Homestay",
                    "taxCode", taxCode,
                    "contactEmail", "hello@nhago.vn",
                    "contactPhone", "0912345678")));
    postAs("/api/admin/business-profiles/" + profile.get("id").asText() + "/approve", admin, null)
        .andExpect(status().isOk());

    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("categoryCode", "homestay");
    body.put("provinceCode", "hanoi");
    body.put("address", "12 Tràng Tiền");
    body.put("latitude", 21.02);
    body.put("longitude", 105.85);
    body.put(
        "images",
        List.of(
            Map.of("url", "https://cdn.example.com/a.jpg", "primary", false),
            Map.of("url", "https://cdn.example.com/cover.jpg", "primary", true)));
    JsonNode listing = json(postAs("/api/business-listings", token, body));
    if (approve) {
      postAs("/api/admin/business-listings/" + listing.get("id").asText() + "/approve", admin, null)
          .andExpect(status().isOk());
    }
    return name;
  }

  @Test
  @DisplayName("partner places appear in the catalogue with their business, cover and status")
  void list_showsPartnerPlace() throws Exception {
    String name = partnerPlace(true);

    getAs(PLACES + "?keyword=" + name + "&source=PARTNER", adminToken())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].name").value(name))
        .andExpect(jsonPath("$.items[0].source").value("PARTNER"))
        .andExpect(jsonPath("$.items[0].businessName").value("Nhà Gỗ Homestay"))
        .andExpect(jsonPath("$.items[0].listingStatus").value("APPROVED"))
        .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.items[0].group.code").value("lodging"))
        .andExpect(jsonPath("$.items[0].coverUrl").value("https://cdn.example.com/cover.jpg"))
        .andExpect(jsonPath("$.items[0].imageCount").value(2))
        .andExpect(jsonPath("$.items[0].reviewCount").value(0));

    getAs(PLACES + "?keyword=" + name + "&source=ADMIN", adminToken())
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  @DisplayName("filters by status and group; pending partner places stay DRAFT")
  void list_filters() throws Exception {
    String name = partnerPlace(false);
    String admin = adminToken();

    getAs(PLACES + "?keyword=" + name + "&status=DRAFT", admin)
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].listingStatus").value("PENDING"));
    getAs(PLACES + "?keyword=" + name + "&status=ACTIVE", admin)
        .andExpect(jsonPath("$.total").value(0));
    getAs(PLACES + "?keyword=" + name + "&group=fnb", admin)
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  @DisplayName("detail returns content and photos; stats count places")
  void detail_andStats() throws Exception {
    String name = partnerPlace(true);
    String admin = adminToken();
    String id =
        json(getAs(PLACES + "?keyword=" + name, admin)).get("items").get(0).get("id").asText();

    getAs(PLACES + "/" + id, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.name").value(name))
        .andExpect(jsonPath("$.latitude").value(21.02))
        .andExpect(jsonPath("$.images.length()").value(2));
    getAs(PLACES + "/999999", admin).andExpect(status().isNotFound());
    getAs(PLACES + "/stats", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").isNumber())
        .andExpect(jsonPath("$.byGroup.lodging").isNumber());
  }

  @Test
  @DisplayName("only admins can read the catalogue")
  void list_adminOnly() throws Exception {
    getAs(PLACES, travellerToken()).andExpect(status().isForbidden());
  }
}
