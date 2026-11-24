package com.dtrps.modules.weather;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.dtrps.modules.discovery.service.EmbeddingGateway;
import com.dtrps.modules.weather.dto.OpenMeteoDtos;
import com.dtrps.modules.weather.service.OpenMeteoGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Integration tests for Weather, Itinerary Weather Impact & Rebuild AI Embeddings (Report 5.2). */
class WeatherAndEmbeddingApiTest extends ApiTestBase {

  @MockitoBean private OpenMeteoGateway openMeteoGateway;
  @MockitoBean private EmbeddingGateway embeddingGateway;

  @Test
  @DisplayName("public weather, itinerary weather impact & admin rebuild embeddings work end-to-end")
  void weatherAndEmbeddings_endToEnd() throws Exception {
    String admin = adminToken();
    String traveller = travellerToken();

    LocalDate today = LocalDate.now();
    OpenMeteoDtos.ForecastResponse mockForecast =
        new OpenMeteoDtos.ForecastResponse(
            new OpenMeteoDtos.Daily(
                List.of(today.toString(), today.plusDays(1).toString()),
                List.of(0, 63),
                List.of(29.0, 24.0),
                List.of(21.0, 19.0),
                List.of(10, 80),
                List.of(0.0, 14.5),
                List.of(12.0, 22.0)),
            new OpenMeteoDtos.Hourly(
                List.of(today + "T09:00", today.plusDays(1) + "T09:00"),
                List.of(0, 63),
                List.of(26.0, 22.0),
                List.of(10, 80),
                List.of(0.0, 5.0),
                List.of(10.0, 20.0)));
    when(openMeteoGateway.forecast(anyDouble(), anyDouble())).thenReturn(mockForecast);

    // Mock 1536-dim embeddings for rebuild
    when(embeddingGateway.embedAll(anyList()))
        .thenAnswer(
            inv -> {
              List<String> texts = inv.getArgument(0);
              return texts.stream().map(t -> new float[1536]).toList();
            });

    // Admin creates an ACTIVE outdoor place
    String placeName = "Công Viên Weather " + UUID.randomUUID().toString().substring(0, 6);
    JsonNode place =
        json(
            postAs(
                    "/api/admin/places",
                    admin,
                    Map.of(
                        "name", placeName,
                        "categoryCode", "park",
                        "provinceCode", "hanoi",
                        "address", "1 Thanh Niên",
                        "latitude", 21.04,
                        "longitude", 105.84,
                        "status", "ACTIVE"))
                .andExpect(status().isCreated()));
    long placeId = place.get("summary").get("id").asLong();

    // 1. Public weather endpoint
    getAs("/api/weather?placeId=" + placeId, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.daily").isArray());

    // 2. Itinerary weather & weather-impact
    JsonNode plan =
        json(
            postAs(
                    "/api/travel-plans",
                    traveller,
                    Map.of(
                        "title", "Chuyến đi check thời tiết",
                        "destination", "Hà Nội",
                        "startDate", today.toString(),
                        "endDate", today.plusDays(1).toString(),
                        "travelerCount", 2,
                        "budgetTotal", 2000000))
                .andExpect(status().isCreated()));
    long planId = plan.get("planId").asLong();
    postAs("/api/travel-plans/" + planId + "/places", traveller, Map.of("placeId", placeId))
        .andExpect(status().isCreated());
    JsonNode itinerary =
        json(
            postAs(
                    "/api/travel-plans/" + planId + "/itineraries",
                    traveller,
                    Map.of("title", "Lịch trình thời tiết"))
                .andExpect(status().isCreated()));
    long itineraryId = itinerary.get("itineraryId").asLong();

    getAs("/api/itineraries/" + itineraryId + "/weather", traveller).andExpect(status().isOk());
    getAs("/api/itineraries/" + itineraryId + "/weather-impact", traveller)
        .andExpect(status().isOk());
    // Unauthenticated rejected
    getAs("/api/itineraries/" + itineraryId + "/weather", null)
        .andExpect(status().isUnauthorized());

    // 3. Rebuild AI Embeddings (non-admin 403, admin 200)
    postAs("/api/admin/embeddings/rebuild?limit=10", traveller, null)
        .andExpect(status().isForbidden());
    postAs("/api/admin/embeddings/rebuild?limit=10", admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.embedded").isNumber());
  }
}
