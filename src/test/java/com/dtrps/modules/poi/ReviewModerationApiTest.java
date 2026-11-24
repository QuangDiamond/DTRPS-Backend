package com.dtrps.modules.poi;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Integration tests for Report Place Reviews & Moderate Reviews (Report 5.2). */
class ReviewModerationApiTest extends ApiTestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("travellers report reviews and admins moderate reviews & reports end-to-end")
  void reportAndModerateReviews_endToEnd() throws Exception {
    String admin = adminToken();
    JsonNode authorSession = session(uniqueEmail());
    long authorId = authorSession.get("user").get("id").asLong();
    String reporterToken = travellerToken();

    // Admin creates an ACTIVE place
    String placeName = "Quán Review " + UUID.randomUUID().toString().substring(0, 8);
    JsonNode place =
        json(
            postAs(
                    "/api/admin/places",
                    admin,
                    Map.of(
                        "name", placeName,
                        "categoryCode", "cafe",
                        "provinceCode", "hanoi",
                        "address", "10 Hàng Đào",
                        "latitude", 21.03,
                        "longitude", 105.85,
                        "status", "ACTIVE"))
                .andExpect(status().isCreated()));
    long placeId = place.get("summary").get("id").asLong();

    // Seed a real VISIBLE review by author in PostgreSQL
    Long reviewId =
        jdbcTemplate.queryForObject(
            "INSERT INTO place_reviews (place_id, user_id, rating, comment, status, created_at) "
                + "VALUES (?, ?, 4, 'Đồ uống ngon, phục vụ nhanh', 'VISIBLE', NOW()) RETURNING review_id",
            Long.class,
            placeId,
            authorId);

    // Unauthenticated cannot report → 401
    postAs(
            "/api/places/" + placeId + "/reviews/" + reviewId + "/reports",
            null,
            Map.of("reason", "SPAM"))
        .andExpect(status().isUnauthorized());

    // Missing review → 404
    postAs(
            "/api/places/" + placeId + "/reviews/999999/reports",
            reporterToken,
            Map.of("reason", "SPAM"))
        .andExpect(status().isNotFound());

    // Reporter reports author's review → 201
    postAs(
            "/api/places/" + placeId + "/reviews/" + reviewId + "/reports",
            reporterToken,
            Map.of("reason", "SPAM"))
        .andExpect(status().isCreated());

    // Reporter sees reported review id
    getAs("/api/places/" + placeId + "/reviews/reported", reporterToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value(reviewId));

    // Non-admin forbidden on moderation endpoints
    getAs("/api/admin/reviews/places", reporterToken).andExpect(status().isForbidden());

    // Admin lists reviews, reports, and trip feedback
    getAs("/api/admin/reviews/places?placeId=" + placeId, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));

    getAs("/api/admin/reviews/reports?status=PENDING&placeId=" + placeId, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));

    getAs("/api/admin/reviews/trips", admin).andExpect(status().isOk());

    // Admin resolves report by hiding review
    putAs(
            "/api/admin/reviews/reports/" + reviewId,
            admin,
            Map.of("action", "HIDE", "reason", "Nội dung quảng cáo spam"))
        .andExpect(status().isOk());

    // Admin restores review with reason
    putAs(
            "/api/admin/reviews/places/" + reviewId + "/status",
            admin,
            Map.of("status", "VISIBLE", "reason", "Đã xác minh lại hợp lệ"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VISIBLE"));
  }
}
