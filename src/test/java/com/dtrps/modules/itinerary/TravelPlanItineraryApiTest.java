package com.dtrps.modules.itinerary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;

/**
 * Integration tests for Travel Plans, Plan Places, Recommendations, Itinerary generation/editing,
 * Trip Progress, Navigation & Trip Feedback (Report 5.2).
 */
class TravelPlanItineraryApiTest extends ApiTestBase {

  @Test
  @DisplayName("full travel plan lifecycle from creation to itinerary, progress and feedback")
  void travelPlanAndItinerary_endToEnd() throws Exception {
    String admin = adminToken();
    String traveller = travellerToken();
    String stranger = travellerToken();

    // Seed an ACTIVE place in Hà Nội
    String placeName = "Điểm Lịch Trình " + UUID.randomUUID().toString().substring(0, 6);
    JsonNode place =
        json(
            postAs(
                    "/api/admin/places",
                    admin,
                    Map.of(
                        "name", placeName,
                        "categoryCode", "cafe",
                        "provinceCode", "hanoi",
                        "address", "50 Đinh Tiên Hoàng",
                        "latitude", 21.0285,
                        "longitude", 105.8542,
                        "status", "ACTIVE"))
                .andExpect(status().isCreated()));
    long placeId = place.get("summary").get("id").asLong();

    // 1. Create, View, Edit Travel Plan
    JsonNode plan =
        json(
            postAs(
                    "/api/travel-plans",
                    traveller,
                    Map.of(
                        "title", "Hà Nội cuối tuần",
                        "destination", "Hà Nội",
                        "startDate", "2026-12-10",
                        "endDate", "2026-12-11",
                        "travelerCount", 2,
                        "budgetTotal", 4000000))
                .andExpect(status().isCreated()));
    long planId = plan.get("planId").asLong();

    getAs("/api/travel-plans", traveller)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].planId").value(planId));
    getAs("/api/travel-plans/" + planId, traveller)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Hà Nội cuối tuần"));
    // Stranger cannot access another user's plan
    getAs("/api/travel-plans/" + planId, stranger).andExpect(status().isNotFound());

    putAs(
            "/api/travel-plans/" + planId,
            traveller,
            Map.of(
                "title", "Hà Nội 2 ngày",
                "destination", "Hà Nội",
                "startDate", "2026-12-10",
                "endDate", "2026-12-11",
                "travelerCount", 2,
                "budgetTotal", 4500000))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Hà Nội 2 ngày"));

    // 2. Candidate places, Recommendations, Partner places & Select/Remove plan places
    getAs("/api/travel-plans/" + planId + "/candidate-places", traveller)
        .andExpect(status().isOk());
    getAs("/api/travel-plans/" + planId + "/recommended-places", traveller)
        .andExpect(status().isOk());
    getAs("/api/travel-plans/" + planId + "/partner-places", traveller)
        .andExpect(status().isOk());

    postAs("/api/travel-plans/" + planId + "/places", traveller, Map.of("placeId", placeId))
        .andExpect(status().isCreated());
    getAs("/api/travel-plans/" + planId + "/places", traveller)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.places.length()").value(1));

    // 3. Generate Itinerary & View Active / Overview
    JsonNode itinerary =
        json(
            postAs(
                    "/api/travel-plans/" + planId + "/itineraries",
                    traveller,
                    Map.of("title", "Lịch trình Hà Nội", "notes", "Đi nhẹ nhàng"))
                .andExpect(status().isCreated()));
    long itineraryId = itinerary.get("itineraryId").asLong();
    long day1Id = itinerary.get("days").get(0).get("dayId").asLong();

    getAs("/api/travel-plans/" + planId + "/itineraries/active", traveller)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itineraryId").value(itineraryId));
    getAs("/api/travel-plans/" + planId + "/itineraries/active/overview", traveller)
        .andExpect(status().isOk());
    getAs("/api/travel-plans/" + planId + "/itineraries/" + itineraryId + "/overview", traveller)
        .andExpect(status().isOk());

    // 4. Manage Itinerary Items (list, create, update, reorder, delete)
    JsonNode items = json(getAs("/api/itinerary-days/" + day1Id + "/items", traveller).andExpect(status().isOk()));
    long firstItemId = items.get(0).get("itemId").asLong();

    JsonNode addedItem =
        json(
            postAs(
                    "/api/itinerary-days/" + day1Id + "/items",
                    traveller,
                    Map.of(
                        "placeId", placeId,
                        "startTime", "15:00",
                        "endTime", "16:30",
                        "estimatedCost", 80000,
                        "notes", "Uống cà phê trứng"))
                .andExpect(status().isCreated()));
    long addedItemId = addedItem.get("itemId").asLong();

    putAs(
            "/api/itinerary-days/" + day1Id + "/items/" + addedItemId,
            traveller,
            Map.of(
                "placeId", placeId,
                "startTime", "15:30",
                "endTime", "17:00",
                "estimatedCost", 90000,
                "notes", "Uống cà phê phố cổ"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notes").value("Uống cà phê phố cổ"));

    putAs(
            "/api/itinerary-days/" + day1Id + "/items/order",
            traveller,
            Map.of("itemIds", List.of(firstItemId, addedItemId)))
        .andExpect(status().isOk());

    deleteAs("/api/itinerary-days/" + day1Id + "/items/" + addedItemId, traveller)
        .andExpect(status().isNoContent());

    // 5. Trip Progress (get, start day 1, mark activity completed, navigation)
    getAs("/api/travel-plans/" + planId + "/trip-progress", traveller).andExpect(status().isOk());
    postAs("/api/travel-plans/" + planId + "/trip-progress/days/1/start", traveller, null)
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/travel-plans/" + planId + "/trip-progress/days/1/activities/" + firstItemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + traveller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
        .andExpect(status().isOk());

    // Navigation endpoint after all stops are handled returns controlled 409 prompting day completion
    getAs(
            "/api/travel-plans/" + planId + "/navigation/days/1/next-route?fromLat=21.028&fromLng=105.854",
            traveller)
        .andExpect(status().isConflict());

    postAs("/api/travel-plans/" + planId + "/trip-progress/days/1/complete", traveller, null)
        .andExpect(status().isOk());
    // Hoàn thành nốt ngày 2 → chuyến đi chuyển COMPLETED
    postAs("/api/travel-plans/" + planId + "/trip-progress/days/2/start", traveller, null)
        .andExpect(status().isOk());
    postAs("/api/travel-plans/" + planId + "/trip-progress/days/2/complete", traveller, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // 6. Submit & Get Trip Feedback on COMPLETED plan
    postAs(
            "/api/travel-plans/" + planId + "/feedback",
            traveller,
            Map.of("rating", 5, "comment", "Lịch trình rất hợp lý và dễ đi"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.rating").value(5));

    getAs("/api/travel-plans/" + planId + "/feedback", traveller)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rating").value(5));

    // 7. Create a second plan to test Remove Place + Change Status (CANCELLED) + Delete Plan
    JsonNode plan2 =
        json(
            postAs(
                    "/api/travel-plans",
                    traveller,
                    Map.of(
                        "title", "Kế hoạch xóa thử",
                        "destination", "Hà Nội",
                        "startDate", "2026-12-20",
                        "endDate", "2026-12-21",
                        "travelerCount", 1,
                        "budgetTotal", 1000000))
                .andExpect(status().isCreated()));
    long plan2Id = plan2.get("planId").asLong();
    postAs("/api/travel-plans/" + plan2Id + "/places", traveller, Map.of("placeId", placeId))
        .andExpect(status().isCreated());
    deleteAs("/api/travel-plans/" + plan2Id + "/places/" + placeId, traveller)
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            patch("/api/travel-plans/" + plan2Id + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + traveller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "CANCELLED"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    deleteAs("/api/travel-plans/" + plan2Id, traveller).andExpect(status().isNoContent());
  }
}
