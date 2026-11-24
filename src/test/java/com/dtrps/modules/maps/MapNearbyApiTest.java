package com.dtrps.modules.maps;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.dtrps.modules.maps.service.GoongGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** API test GET /api/maps/nearby và vehicle của /directions — sheet Maps, UTC017–UTC023, UTC029. */
class MapNearbyApiTest extends ApiTestBase {
  @MockitoBean private GoongGateway goong;

  private void goongHasNoResults() throws Exception {
    when(goong.get(eq("/Place/AutoComplete"), any()))
        .thenReturn(objectMapper.readTree("{\"predictions\":[]}"));
  }

  @Test
  @DisplayName("UTC017 Không truyền keyword/radius → mặc định 'cây xăng' và 5 km")
  void defaults() throws Exception {
    when(goong.get(
            eq("/Place/AutoComplete"),
            argThat(p -> "cây xăng".equals(p.get("input")) && "5".equals(p.get("radius")))))
        .thenReturn(objectMapper.readTree("{\"predictions\":[]}"));

    getAs("/api/maps/nearby?lat=16.05&lng=108.2", travellerToken())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @DisplayName("UTC018 lat=91 → 400")
  void invalidLatitude() throws Exception {
    getAs("/api/maps/nearby?lat=91&lng=108.2", travellerToken()).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("UTC019 keyword 1 ký tự → 400")
  void keywordTooShort() throws Exception {
    getAs("/api/maps/nearby?lat=16.05&lng=108.2&keyword=a", travellerToken())
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("UTC020 radius=0 → 400")
  void radiusBelowMinimum() throws Exception {
    getAs("/api/maps/nearby?lat=16.05&lng=108.2&radius=0", travellerToken())
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("UTC021 radius=31 → 400")
  void radiusAboveMaximum() throws Exception {
    getAs("/api/maps/nearby?lat=16.05&lng=108.2&radius=31", travellerToken())
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("UTC022 radius=30 → chấp nhận")
  void radiusAtMaximum() throws Exception {
    goongHasNoResults();

    getAs("/api/maps/nearby?lat=16.05&lng=108.2&radius=30", travellerToken())
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("UTC023 Chưa đăng nhập → 401")
  void requiresLogin() throws Exception {
    mockMvc
        .perform(get("/api/maps/nearby?lat=16.05&lng=108.2"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("UTC029 directions với vehicle=plane → 400")
  void directionsRejectsUnknownVehicle() throws Exception {
    getAs(
            "/api/maps/directions?fromLat=16.05&fromLng=108.2&toLat=16.06&toLng=108.22&vehicle=plane",
            travellerToken())
        .andExpect(status().isBadRequest());
  }
}
