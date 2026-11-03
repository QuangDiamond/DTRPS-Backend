package com.dtrps.modules.businessowner;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListingRevisionApiTest extends ApiTestBase {
  private static final String LISTING = "/api/business-listing";
  private static final String REVISIONS = LISTING + "/revisions";
  private static final String ADMIN = "/api/admin/listing-revisions";

  private static Map<String, Object> placeBody(String name) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("categoryCode", "cafe");
    body.put("provinceCode", "hanoi");
    body.put("address", "5 Hàng Bè, Hoàn Kiếm");
    body.put("latitude", 21.0333);
    body.put("longitude", 105.8521);
    body.put(
        "openingHours",
        List.of(
            Map.of("dayOfWeek", 1, "openTime", "07:00", "closeTime", "22:00", "closed", false),
            Map.of("dayOfWeek", 7, "closed", true)));
    body.put(
        "prices",
        List.of(Map.of("priceType", "avg_drink", "amount", 45000, "description", "Đồ uống")));
    body.put(
        "images",
        List.of(
            Map.of("url", "https://cdn.example.com/a.jpg", "primary", false),
            Map.of("url", "https://cdn.example.com/b.jpg", "primary", false)));
    return body;
  }

  /** An ACTIVE business; approvePlace = true also publishes its place. */
  private String owner(boolean approvePlace) throws Exception {
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
                    "businessName", "Mộc Phố Coffee",
                    "taxCode", taxCode,
                    "contactEmail", "hello@mocpho.vn",
                    "contactPhone", "0912345678")));
    postAs("/api/admin/business-profiles/" + profile.get("id").asText() + "/approve", admin, null)
        .andExpect(status().isOk());
    JsonNode listing = json(postAs(LISTING, token, placeBody("Mộc Phố Coffee")));
    if (approvePlace) {
      postAs("/api/admin/business-listings/" + listing.get("id").asText() + "/approve", admin, null)
          .andExpect(status().isOk());
    }
    return token;
  }

  @Test
  @DisplayName("hours, prices and photos are saved with the place; the first photo is the cover")
  void submit_savesDetails() throws Exception {
    getAs(LISTING, owner(false))
        .andExpect(jsonPath("$.openingHours.length()").value(2))
        .andExpect(jsonPath("$.openingHours[0].openTime").value("07:00"))
        .andExpect(jsonPath("$.openingHours[1].closed").value(true))
        .andExpect(jsonPath("$.prices[0].priceType").value("AVG_DRINK"))
        .andExpect(jsonPath("$.prices[0].currency").value("VND"))
        .andExpect(jsonPath("$.images.length()").value(2))
        .andExpect(jsonPath("$.images[0].primary").value(true));
  }

  @Test
  @DisplayName("details are checked: one entry per day, times when open, one cover")
  void details_validated() throws Exception {
    String token = owner(false);

    Map<String, Object> duplicateDay = placeBody("Trùng ngày");
    duplicateDay.put(
        "openingHours",
        List.of(
            Map.of("dayOfWeek", 2, "openTime", "07:00", "closeTime", "22:00", "closed", false),
            Map.of("dayOfWeek", 2, "closed", true)));
    putAs(LISTING, token, duplicateDay).andExpect(status().isBadRequest());

    Map<String, Object> missingTimes = placeBody("Thiếu giờ");
    missingTimes.put("openingHours", List.of(Map.of("dayOfWeek", 3, "closed", false)));
    putAs(LISTING, token, missingTimes).andExpect(status().isBadRequest());

    Map<String, Object> twoCovers = placeBody("Hai ảnh bìa");
    twoCovers.put(
        "images",
        List.of(
            Map.of("url", "https://cdn.example.com/a.jpg", "primary", true),
            Map.of("url", "https://cdn.example.com/b.jpg", "primary", true)));
    putAs(LISTING, token, twoCovers).andExpect(status().isBadRequest());

    Map<String, Object> notHttps = placeBody("Ảnh http");
    notHttps.put("images", List.of(Map.of("url", "http://cdn.example.com/a.jpg")));
    putAs(LISTING, token, notHttps).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("revisions are only for approved places, one pending at a time")
  void propose_rules() throws Exception {
    postAs(REVISIONS, owner(false), placeBody("Chưa duyệt")).andExpect(status().isConflict());

    String token = owner(true);
    postAs(REVISIONS, token, placeBody("Đề xuất 1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"));
    postAs(REVISIONS, token, placeBody("Đề xuất 2")).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("the owner withdraws a pending revision and can then propose again")
  void cancel_thenProposeAgain() throws Exception {
    String token = owner(true);
    String id = json(postAs(REVISIONS, token, placeBody("Đề xuất"))).get("id").asText();

    postAs(REVISIONS + "/" + id + "/cancel", token, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    postAs(REVISIONS + "/" + id + "/cancel", token, null).andExpect(status().isConflict());
    postAs(REVISIONS + "/" + id + "/cancel", owner(true), null).andExpect(status().isNotFound());

    postAs(REVISIONS, token, placeBody("Đề xuất mới")).andExpect(status().isCreated());
    getAs(REVISIONS, token).andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName("a rejection gives the owner a reason code and feedback, never the internal note")
  void reject_showsFeedback() throws Exception {
    String token = owner(true);
    String id = json(postAs(REVISIONS, token, placeBody("Giá mới"))).get("id").asText();
    String admin = adminToken();

    postAs(ADMIN + "/" + id + "/reject", admin, Map.of("reasonCode", "pricing"))
        .andExpect(status().isBadRequest());
    // review_note là bắt buộc khi từ chối
    postAs(
            ADMIN + "/" + id + "/reject",
            admin,
            Map.of("reasonCode", "pricing", "partnerFeedback", "Giá chưa khớp menu thực tế"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.note").exists());
    postAs(
            ADMIN + "/" + id + "/reject",
            admin,
            Map.of(
                "reasonCode", "pricing",
                "partnerFeedback", "Giá chưa khớp menu thực tế",
                "note", "Đã gọi xác minh"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.reviewNote").value("Đã gọi xác minh"));

    getAs(REVISIONS, token)
        .andExpect(jsonPath("$[0].rejectReasonCode").value("pricing"))
        .andExpect(jsonPath("$[0].partnerFeedback").value("Giá chưa khớp menu thực tế"))
        .andExpect(jsonPath("$[0].reviewNote").isEmpty());
    getAs(LISTING, token).andExpect(jsonPath("$.name").value("Mộc Phố Coffee"));
  }

  @Test
  @DisplayName("approval copies the proposed content, hours, prices and photos to the place")
  void approve_appliesChanges() throws Exception {
    String token = owner(true);
    Map<String, Object> change = placeBody("Mộc Phố Coffee & Books");
    change.put("categoryCode", "restaurant");
    change.put("website", "https://mocpho.vn");
    change.put(
        "openingHours",
        List.of(
            Map.of("dayOfWeek", 5, "openTime", "18:00", "closeTime", "02:00", "closed", false)));
    change.put("prices", List.of());
    change.put(
        "images", List.of(Map.of("url", "https://cdn.example.com/new.jpg", "primary", true)));
    String id = json(postAs(REVISIONS, token, change)).get("id").asText();
    String admin = adminToken();

    getAs(ADMIN + "?status=PENDING&keyword=Books", admin).andExpect(jsonPath("$.total").value(1));
    getAs(ADMIN + "/" + id, admin)
        .andExpect(jsonPath("$.revision.proposed.name").value("Mộc Phố Coffee & Books"))
        .andExpect(jsonPath("$.current.name").value("Mộc Phố Coffee"));

    postAs(ADMIN + "/" + id + "/approve", admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision.status").value("APPROVED"));

    getAs(LISTING, token)
        .andExpect(jsonPath("$.name").value("Mộc Phố Coffee & Books"))
        .andExpect(jsonPath("$.category.code").value("restaurant"))
        .andExpect(jsonPath("$.website").value("https://mocpho.vn"))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.placeStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.openingHours.length()").value(1))
        .andExpect(jsonPath("$.openingHours[0].closeTime").value("02:00"))
        .andExpect(jsonPath("$.prices.length()").value(0))
        .andExpect(jsonPath("$.images[0].url").value("https://cdn.example.com/new.jpg"));

    postAs(ADMIN + "/" + id + "/approve", admin, null).andExpect(status().isConflict());
    getAs(ADMIN, travellerToken()).andExpect(status().isForbidden());
  }
}
