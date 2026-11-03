package com.dtrps.modules.businessowner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessListingApiTest extends ApiTestBase {
  private static final String OWN = "/api/business-listings";
  private static final String ADMIN = "/api/admin/business-listings";

  private static Map<String, Object> placeBody(String name) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("categoryCode", "homestay");
    body.put("provinceCode", "danang");
    body.put("address", "12 Trần Phú, Hội An");
    body.put("description", "Homestay giữa phố cổ");
    body.put("latitude", 15.8794);
    body.put("longitude", 108.335);
    body.put("phone", "0905128991");
    body.put("website", "https://hoiangarden.vn");
    return body;
  }

  /** A user with a business profile; approved = true makes the profile ACTIVE. */
  private String ownerToken(boolean approved) throws Exception {
    String token = travellerToken();
    String taxCode =
        String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
    JsonNode profile =
        json(
            postAs(
                "/api/business-profile",
                token,
                Map.of(
                    "businessName", "Hội An Garden",
                    "taxCode", taxCode,
                    "contactEmail", "lienhe@hoiangarden.vn",
                    "contactPhone", "0905128991")));
    if (approved) {
      postAs(
              "/api/admin/business-profiles/" + profile.get("id").asText() + "/approve",
              adminToken(),
              null)
          .andExpect(status().isOk());
    }
    return token;
  }

  @Test
  @DisplayName("an active business submits its place: listing PENDING, place DRAFT")
  void submit_createsPendingListing() throws Exception {
    String token = ownerToken(true);

    postAs(OWN, token, placeBody("Hội An Garden Homestay"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.placeStatus").value("DRAFT"))
        .andExpect(jsonPath("$.category.code").value("homestay"))
        .andExpect(jsonPath("$.group.code").value("lodging"))
        .andExpect(jsonPath("$.province.code").value("danang"));

    getAs(OWN, token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Hội An Garden Homestay"));
  }

  @Test
  @DisplayName("only an ACTIVE business may submit; a business may own several places")
  void submit_rules() throws Exception {
    postAs(OWN, travellerToken(), placeBody("Không có hồ sơ")).andExpect(status().isForbidden());
    postAs(OWN, ownerToken(false), placeBody("Hồ sơ chờ duyệt")).andExpect(status().isForbidden());

    String token = ownerToken(true);
    postAs(OWN, token, placeBody("Địa điểm 1")).andExpect(status().isCreated());
    postAs(OWN, token, placeBody("Địa điểm 2")).andExpect(status().isCreated());
    getAs(OWN, token)
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").value("Địa điểm 2"));

    // Another owner cannot read or edit these places
    String firstId = json(getAs(OWN, token)).get(1).get("id").asText();
    String stranger = ownerToken(true);
    getAs(OWN + "/" + firstId, stranger).andExpect(status().isNotFound());
    putAs(OWN + "/" + firstId, stranger, placeBody("Chiếm quyền")).andExpect(status().isNotFound());
    getAs(OWN, stranger).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("the place must use a subtype and a known province, with valid fields")
  void submit_validates() throws Exception {
    String token = ownerToken(true);

    Map<String, Object> group = placeBody("Nhóm thay vì loại con");
    group.put("categoryCode", "lodging");
    postAs(OWN, token, group).andExpect(status().isBadRequest());

    Map<String, Object> province = placeBody("Tỉnh cũ");
    province.put("provinceCode", "quangnam");
    postAs(OWN, token, province).andExpect(status().isBadRequest());

    Map<String, Object> invalid = placeBody("");
    invalid.put("latitude", 120);
    invalid.put("website", "hoiangarden.vn");
    postAs(OWN, token, invalid)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.name").isNotEmpty())
        .andExpect(jsonPath("$.errors.latitude").isNotEmpty())
        .andExpect(jsonPath("$.errors.website").isNotEmpty());
  }

  @Test
  @DisplayName("reject shows the reason; the owner edits and resubmits; approval publishes")
  void rejectResubmitApprove() throws Exception {
    String token = ownerToken(true);
    String id = json(postAs(OWN, token, placeBody("Hội An Garden"))).get("id").asText();
    String admin = adminToken();

    postAs(ADMIN + "/" + id + "/reject", admin, Map.of("reason", " "))
        .andExpect(status().isBadRequest());
    postAs(ADMIN + "/" + id + "/reject", admin, Map.of("reason", "Ảnh và tọa độ chưa khớp"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
    getAs(OWN + "/" + id, token)
        .andExpect(jsonPath("$.statusNote").value("Ảnh và tọa độ chưa khớp"));

    putAs(OWN + "/" + id, token, placeBody("Hội An Garden Homestay"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.statusNote").isEmpty());

    postAs(ADMIN + "/" + id + "/approve", admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.placeStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.approvedAt").isNotEmpty());

    postAs(ADMIN + "/" + id + "/approve", admin, null).andExpect(status().isConflict());
    putAs(OWN + "/" + id, token, placeBody("Sửa sau khi duyệt")).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("admins filter listings and read the counts")
  void adminListAndStats() throws Exception {
    String admin = adminToken();
    JsonNode before = json(getAs(ADMIN + "/stats", admin));
    String name = "Quán Cà Phê " + ThreadLocalRandom.current().nextInt(100_000);
    postAs(OWN, ownerToken(true), placeBody(name)).andExpect(status().isCreated());

    getAs(ADMIN + "?status=PENDING&keyword=" + name, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].businessName").value("Hội An Garden"));

    JsonNode after = json(getAs(ADMIN + "/stats", admin));
    assertThat(after.get("pending").asLong()).isEqualTo(before.get("pending").asLong() + 1);
    getAs(ADMIN, travellerToken()).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("reference lists: 34 provinces and 4 groups with 17 subtypes")
  void referenceLists() throws Exception {
    String token = travellerToken();
    getAs("/api/reference/provinces", token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(34));

    JsonNode groups = json(getAs("/api/reference/categories", token).andExpect(status().isOk()));
    assertThat(groups).hasSize(4);
    int subtypes = 0;
    for (JsonNode group : groups) {
      subtypes += group.get("subtypes").size();
    }
    assertThat(subtypes).isEqualTo(17);
    assertThat(groups.get(0).get("code").asText()).isEqualTo("lodging");
  }
}
