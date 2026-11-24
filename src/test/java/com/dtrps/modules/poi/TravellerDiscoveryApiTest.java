package com.dtrps.modules.poi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Integration tests for Reference Data, Explore Destinations, Place Details, Preferences,
 * Favorites & Place Reviews (Report 5.2).
 */
class TravellerDiscoveryApiTest extends ApiTestBase {

  @Test
  @DisplayName("reference endpoints return provinces, categories, interests and special needs")
  void referenceEndpoints_returnSeededData() throws Exception {
    getAs("/api/reference/provinces", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(34));

    getAs("/api/reference/categories", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4));

    getAs("/api/reference/interests", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());

    getAs("/api/reference/special-needs", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @DisplayName("traveller explores places, views details, manages preferences, favorites & reviews")
  void discoveryPreferencesFavoritesAndReviews_endToEnd() throws Exception {
    String admin = adminToken();
    String traveller = travellerToken();

    // Admin creates an ACTIVE place
    String name = "Điểm Khám Phá " + UUID.randomUUID().toString().substring(0, 6);
    JsonNode created =
        json(
            postAs(
                    "/api/admin/places",
                    admin,
                    Map.of(
                        "name", name,
                        "categoryCode", "cafe",
                        "provinceCode", "hanoi",
                        "address", "15 Hàng Bạc",
                        "latitude", 21.034,
                        "longitude", 105.853,
                        "status", "ACTIVE"))
                .andExpect(status().isCreated()));
    long placeId = created.get("summary").get("id").asLong();

    // Public search & place details
    getAs("/api/places?keyword=" + name, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(placeId));

    getAs("/api/places/" + placeId, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.name").value(name));

    getAs("/api/places/999999", null).andExpect(status().isNotFound());

    // Travel preferences
    getAs("/api/users/me/preferences", null).andExpect(status().isUnauthorized());
    getAs("/api/users/me/preferences", traveller).andExpect(status().isOk());

    JsonNode interests = json(getAs("/api/reference/interests", null));
    String firstInterest = interests.get(0).get("code").asText();
    putAs(
            "/api/users/me/preferences",
            traveller,
            Map.of("interestCodes", List.of(firstInterest)))
        .andExpect(status().isOk());

    // Favorites: add, list, remove
    putAs("/api/users/me/favorites/" + placeId, traveller, null).andExpect(status().isNoContent());
    getAs("/api/users/me/favorites", traveller)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(placeId));
    deleteAs("/api/users/me/favorites/" + placeId, traveller).andExpect(status().isNoContent());
    getAs("/api/users/me/favorites", traveller)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));

    // Place reviews: public list + /me status + review without completed visit is rejected
    getAs("/api/places/" + placeId + "/reviews", null).andExpect(status().isOk());
    getAs("/api/places/" + placeId + "/reviews/me", traveller)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canReview").value(false));

    mockMvc
        .perform(
            multipart("/api/places/" + placeId + "/reviews")
                .param("rating", "5")
                .param("comment", "Chưa đi nhưng thử đánh giá")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + traveller))
        .andExpect(status().isForbidden());
  }
}
