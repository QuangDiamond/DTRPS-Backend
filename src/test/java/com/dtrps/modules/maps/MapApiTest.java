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

class MapApiTest extends ApiTestBase {
  @MockitoBean private GoongGateway goong;

  @Test
  @DisplayName("autocomplete trims Goong predictions and passes the nearby point")
  void autocomplete() throws Exception {
    when(goong.get(
            eq("/Place/AutoComplete"),
            argThat(params -> "21.0,105.8".equals(params.get("location")))))
        .thenReturn(
            objectMapper.readTree(
                """
                {"predictions":[{"place_id":"p1","description":"Hồ Hoàn Kiếm, Hà Nội",
                 "structured_formatting":{"main_text":"Hồ Hoàn Kiếm","secondary_text":"Hà Nội"}}]}
                """));

    getAs("/api/maps/autocomplete?input=Hoan Kiem&lat=21.0&lng=105.8", travellerToken())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].placeId").value("p1"))
        .andExpect(jsonPath("$[0].title").value("Hồ Hoàn Kiếm"))
        .andExpect(jsonPath("$[0].subtitle").value("Hà Nội"));
  }

  @Test
  @DisplayName("place detail and reverse geocoding return coordinates and address")
  void placeAndReverse() throws Exception {
    when(goong.get(eq("/Place/Detail"), any()))
        .thenReturn(
            objectMapper.readTree(
                """
                {"result":{"place_id":"p1","name":"Văn Miếu","formatted_address":"58 Quốc Tử Giám",
                 "geometry":{"location":{"lat":21.0294,"lng":105.8355}}}}
                """));
    when(goong.get(eq("/Geocode"), any()))
        .thenReturn(
            objectMapper.readTree(
                """
                {"results":[{"place_id":"g1","formatted_address":"Hàng Trống, Hoàn Kiếm, Hà Nội",
                 "geometry":{"location":{"lat":21.0286,"lng":105.8521}}}]}
                """));
    String token = travellerToken();

    getAs("/api/maps/places/p1", token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.latitude").value(21.0294))
        .andExpect(jsonPath("$.address").value("58 Quốc Tử Giám"));
    getAs("/api/maps/reverse?lat=21.0287&lng=105.8524", token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address").value("Hàng Trống, Hoàn Kiếm, Hà Nội"))
        .andExpect(jsonPath("$.latitude").value(21.0287));
  }

  @Test
  @DisplayName("directions sum the legs; unknown vehicles are rejected")
  void directions() throws Exception {
    when(goong.get(eq("/Direction"), any()))
        .thenReturn(
            objectMapper.readTree(
                """
                {"routes":[{"legs":[{"distance":{"text":"2.67 km","value":2671},
                 "duration":{"text":"9 phút","value":556}}],"overview_polyline":{"points":"abc"}}]}
                """));
    String token = travellerToken();
    String route =
        "/api/maps/directions?fromLat=21.0287&fromLng=105.8524&toLat=21.0294&toLng=105.8355";

    getAs(route, token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.distanceMeters").value(2671))
        .andExpect(jsonPath("$.durationSeconds").value(556))
        .andExpect(jsonPath("$.polyline").value("abc"));
    getAs(route + "&vehicle=plane", token).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("map services need a signed-in user")
  void requiresLogin() throws Exception {
    mockMvc
        .perform(get("/api/maps/autocomplete?input=Ha Noi"))
        .andExpect(status().isUnauthorized());
  }
}
