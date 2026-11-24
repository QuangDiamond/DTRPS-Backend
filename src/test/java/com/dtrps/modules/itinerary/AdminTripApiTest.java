package com.dtrps.modules.itinerary;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Integration tests for Monitor Travel Plans (Report 5.2). */
class AdminTripApiTest extends ApiTestBase {

  @Test
  @DisplayName("admin monitors travel plans, stats, destinations and details; non-admin forbidden")
  void monitorTravelPlans_endToEnd() throws Exception {
    String traveller = travellerToken();
    String admin = adminToken();

    // Traveller creates a real travel plan stored in PostgreSQL
    JsonNode created =
        json(
            postAs(
                    "/api/travel-plans",
                    traveller,
                    Map.of(
                        "title", "Chuyến đi Đà Nẵng Test",
                        "destination", "Đà Nẵng",
                        "startDate", "2026-12-01",
                        "endDate", "2026-12-03",
                        "travelerCount", 2,
                        "budgetTotal", 5000000))
                .andExpect(status().isCreated()));
    long planId = created.get("planId").asLong();

    getAs("/api/admin/trips?keyword=Đà Nẵng", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").isNumber());

    getAs("/api/admin/trips/stats", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").isNumber());

    getAs("/api/admin/trips/destinations", admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());

    getAs("/api/admin/trips/" + planId, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.planId").value(planId))
        .andExpect(jsonPath("$.summary.destination").value("Đà Nẵng"));

    // Unknown plan ID → 404
    getAs("/api/admin/trips/999999", admin).andExpect(status().isNotFound());

    // Non-admin cannot access admin trip endpoints
    getAs("/api/admin/trips", traveller).andExpect(status().isForbidden());
    getAs("/api/admin/trips/" + planId, traveller).andExpect(status().isForbidden());
  }
}
