package com.dtrps.modules.maps.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dtrps.modules.maps.dto.RouteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Turn-by-turn parsing of Goong's Direction payload; no Spring context or database needed. */
class MapServiceDirectionsTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private static MapService serviceReturning(String payload) {
    GoongGateway gateway =
        new GoongGateway() {
          @Override
          public JsonNode get(String path, Map<String, String> params) {
            try {
              return JSON.readTree(payload);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          }
        };
    return new MapService(gateway);
  }

  @Test
  @DisplayName("directions keep the turn-by-turn steps in plain text, across legs")
  void parsesSteps() {
    MapService service =
        serviceReturning(
            """
            {"routes":[{"legs":[
              {"distance":{"text":"1 km","value":1000},"duration":{"text":"3 phút","value":180},
               "steps":[
                {"html_instructions":"Rẽ <b>phải</b> vào <b>Hàng Bài</b><div style='font-size:0.9em'>Điểm đến ở bên trái</div>",
                 "maneuver":"turn-right","distance":{"text":"300 m","value":300},
                 "duration":{"value":60},
                 "start_location":{"lat":21.0285,"lng":105.8542},
                 "end_location":{"lat":21.0270,"lng":105.8550}},
                {"html_instructions":"Đi thẳng","distance":{"value":700},"duration":{"value":120},
                 "start_location":{"lat":21.0270,"lng":105.8550},
                 "end_location":{"lat":21.0210,"lng":105.8560}},
                {"html_instructions":"Bước thiếu toạ độ"}
               ]},
              {"distance":{"text":"500 m","value":500},"duration":{"text":"2 phút","value":90},
               "steps":[
                {"html_instructions":"Đến nơi","distance":{"value":500},"duration":{"value":90},
                 "start_location":{"lat":21.0210,"lng":105.8560},
                 "end_location":{"lat":21.0200,"lng":105.8570}}
               ]}
            ],"overview_polyline":{"points":"abc"}}]}
            """);

    RouteResponse route = service.directions(21.0285, 105.8542, 21.0200, 105.8570, "car");

    assertThat(route.distanceMeters()).isEqualTo(1500);
    assertThat(route.durationSeconds()).isEqualTo(270);
    assertThat(route.steps()).hasSize(3);
    assertThat(route.steps().get(0).instruction())
        .isEqualTo("Rẽ phải vào Hàng Bài Điểm đến ở bên trái");
    assertThat(route.steps().get(0).maneuver()).isEqualTo("turn-right");
    assertThat(route.steps().get(0).distanceMeters()).isEqualTo(300);
    assertThat(route.steps().get(0).startLatitude()).isEqualTo(21.0285);
    assertThat(route.steps().get(1).maneuver()).isNull();
    assertThat(route.steps().get(2).instruction()).isEqualTo("Đến nơi");
  }

  @Test
  @DisplayName("a route without steps still works and returns an empty step list")
  void routeWithoutSteps() {
    MapService service =
        serviceReturning(
            """
            {"routes":[{"legs":[{"distance":{"text":"2.67 km","value":2671},
             "duration":{"text":"9 phút","value":556}}],"overview_polyline":{"points":"abc"}}]}
            """);

    RouteResponse route = service.directions(21.0287, 105.8524, 21.0294, 105.8355, "car");

    assertThat(route.distanceMeters()).isEqualTo(2671);
    assertThat(route.steps()).isEmpty();
  }

  @Test
  @DisplayName("plainText removes tags and entities")
  void plainText() {
    assertThat(MapService.plainText("Rẽ <b>trái</b>&nbsp;vào A &amp; B")).isEqualTo("Rẽ trái vào A & B");
    assertThat(MapService.plainText(null)).isEmpty();
    assertThat(MapService.plainText("  ")).isEmpty();
  }

  @Test
  @DisplayName("haversine distance matches a known Hanoi to Ho Chi Minh City distance")
  void geoDistance() {
    double meters = GeoDistance.meters(21.0285, 105.8542, 10.7769, 106.7009);
    assertThat(meters).isBetween(1_130_000.0, 1_160_000.0);
    assertThat(GeoDistance.meters(21.0, 105.0, 21.0, 105.0)).isZero();
  }
}
