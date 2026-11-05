package com.dtrps.modules.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class NotificationApiTest extends ApiTestBase {
  private static final String NOTIFICATIONS = "/api/notifications";

  private static Map<String, Object> placeBody(String name) {
    return Map.of(
        "name",
        name,
        "categoryCodes",
        List.of("cafe"),
        "provinceCode",
        "hanoi",
        "address",
        "5 Hàng Bè, Hoàn Kiếm",
        "latitude",
        21.0333,
        "longitude",
        105.8521);
  }

  private static List<JsonNode> items(JsonNode page) {
    return StreamSupport.stream(page.get("items").spliterator(), false).toList();
  }

  @Test
  @DisplayName("partner submissions notify admins; admin decisions notify the partner")
  void reviewFlow_createsNotifications() throws Exception {
    String partner = travellerToken();
    String admin = adminToken();
    String taxCode =
        String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
    JsonNode profile =
        json(
            postAs(
                "/api/business-profile",
                partner,
                Map.of(
                    "businessName", "Thông báo Coffee",
                    "taxCode", taxCode,
                    "contactEmail", "hello@tb.vn",
                    "contactPhone", "0912345678")));
    String businessLink = "/admin/partners/" + profile.get("id").asText();
    assertThat(items(json(getAs(NOTIFICATIONS + "?size=50", admin))))
        .anySatisfy(
            n -> {
              assertThat(n.get("type").asText()).isEqualTo("PARTNER_SUBMITTED");
              assertThat(n.get("link").asText()).isEqualTo(businessLink);
              assertThat(n.get("read").asBoolean()).isFalse();
            });

    postAs("/api/admin/business-profiles/" + profile.get("id").asText() + "/approve", admin, null)
        .andExpect(status().isOk());
    JsonNode listing = json(postAs("/api/business-listings", partner, placeBody("Quán TB")));
    String listingLink = "/admin/listings/" + listing.get("id").asText();
    assertThat(items(json(getAs(NOTIFICATIONS + "?size=50", admin))))
        .anySatisfy(n -> assertThat(n.get("link").asText()).isEqualTo(listingLink));

    postAs("/api/admin/business-listings/" + listing.get("id").asText() + "/approve", admin, null)
        .andExpect(status().isOk());

    JsonNode mine = json(getAs(NOTIFICATIONS, partner));
    assertThat(items(mine))
        .extracting(n -> n.get("type").asText())
        .containsExactly("LISTING_APPROVED", "PARTNER_APPROVED");
    assertThat(mine.get("unread").asLong()).isEqualTo(2);
    assertThat(items(mine).get(0).get("link").asText())
        .isEqualTo("/partner/locations/" + listing.get("id").asText());

    long first = items(mine).get(0).get("id").asLong();
    postAs(NOTIFICATIONS + "/" + first + "/read", partner, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.read").value(true));
    getAs(NOTIFICATIONS + "/unread-count", partner).andExpect(jsonPath("$.unread").value(1));
    getAs(NOTIFICATIONS + "?unread=true", partner).andExpect(jsonPath("$.total").value(1));

    postAs(NOTIFICATIONS + "/read-all", partner, null).andExpect(status().isOk());
    getAs(NOTIFICATIONS + "/unread-count", partner).andExpect(jsonPath("$.unread").value(0));

    // Không đọc được thông báo của người khác
    postAs(NOTIFICATIONS + "/" + first + "/read", admin, null).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("the event stream opens for a signed-in user and needs a token")
  void stream_needsSignIn() throws Exception {
    mockMvc
        .perform(
            get(NOTIFICATIONS + "/stream")
                .header("Authorization", "Bearer " + travellerToken())
                .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(request().asyncStarted());
    mockMvc
        .perform(get(NOTIFICATIONS + "/stream").accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isUnauthorized());
  }
}
