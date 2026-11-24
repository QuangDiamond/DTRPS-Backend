package com.dtrps.modules.maps.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.modules.maps.dto.NearbyPlace;
import com.dtrps.modules.maps.dto.RouteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit test MapService.nearby (UTC010–UTC016) và MapService.directions với tuyến thay thế, vehicle,
 * steps (UTC024–UTC028) — sheet Maps trong Report5.1.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapServiceNearbyTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final double LAT = 16.0544;
  private static final double LNG = 108.2022;
  private static final double METERS_PER_DEGREE = 111_195.0;

  @Mock private GoongGateway goong;

  private MapService service;

  @BeforeEach
  void setUp() {
    service = new MapService(goong);
  }

  private JsonNode json(String text) throws Exception {
    return JSON.readTree(text);
  }

  private static String prediction(String placeId) {
    return "{\"place_id\":\""
        + placeId
        + "\",\"structured_formatting\":{\"main_text\":\"Gợi ý "
        + placeId
        + "\"}}";
  }

  private void autocompleteReturns(String... predictions) throws Exception {
    when(goong.get(eq("/Place/AutoComplete"), any()))
        .thenReturn(json("{\"predictions\":[" + String.join(",", predictions) + "]}"));
  }

  /** Chi tiết một địa điểm nằm cách điểm tìm {@code metersNorth} mét về phía bắc. */
  private void detail(String placeId, String name, double metersNorth) throws Exception {
    double lat = LAT + metersNorth / METERS_PER_DEGREE;
    String nameField = name == null ? "" : "\"name\":\"" + name + "\",";
    when(goong.get(
            eq("/Place/Detail"), argThat(p -> p != null && placeId.equals(p.get("place_id")))))
        .thenReturn(
            json(
                "{\"result\":{\"place_id\":\""
                    + placeId
                    + "\","
                    + nameField
                    + "\"formatted_address\":\"Địa chỉ "
                    + placeId
                    + "\",\"geometry\":{\"location\":{\"lat\":"
                    + lat
                    + ",\"lng\":"
                    + LNG
                    + "}}}}"));
  }

  // ---- nearby ----

  @Test
  @DisplayName("UTC010 3 kết quả hợp lệ → sắp xếp theo khoảng cách tăng dần")
  void nearby_sortedByDistance() throws Exception {
    autocompleteReturns(prediction("far"), prediction("near"), prediction("mid"));
    detail("far", "Xăng Xa", 3000);
    detail("near", "Xăng Gần", 500);
    detail("mid", "Xăng Giữa", 1500);

    List<NearbyPlace> result = service.nearby(LAT, LNG, "cây xăng", 5);

    assertThat(result).extracting(NearbyPlace::placeId).containsExactly("near", "mid", "far");
    assertThat(result.get(0).name()).isEqualTo("Xăng Gần");
    assertThat(result.get(0).address()).isEqualTo("Địa chỉ near");
    assertThat(result.get(0).distanceMeters()).isBetween(495L, 505L);
  }

  @Test
  @DisplayName("UTC011 Chi tiết không có name → dùng main_text của gợi ý")
  void nearby_nameFallsBackToMainText() throws Exception {
    autocompleteReturns(prediction("p1"));
    // Goong trả tên rỗng: MapService lấy formatted_address làm name khi thiếu "name",
    // nên ép tên rỗng để kiểm tra nhánh dùng structured_formatting.main_text.
    when(goong.get(eq("/Place/Detail"), any()))
        .thenReturn(
            json(
                "{\"result\":{\"place_id\":\"p1\",\"name\":\"\",\"formatted_address\":\"\","
                    + "\"geometry\":{\"location\":{\"lat\":"
                    + (LAT + 300 / METERS_PER_DEGREE)
                    + ",\"lng\":"
                    + LNG
                    + "}}}}"));

    List<NearbyPlace> result = service.nearby(LAT, LNG, "cây xăng", 5);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("Gợi ý p1");
  }

  @Test
  @DisplayName("UTC012 Goong trả 8 kết quả → chỉ lấy tối đa 5")
  void nearby_limitedToFive() throws Exception {
    String[] ids = {"a", "b", "c", "d", "e", "f", "g", "h"};
    String[] predictions = new String[ids.length];
    for (int i = 0; i < ids.length; i++) {
      predictions[i] = prediction(ids[i]);
      detail(ids[i], "Xăng " + ids[i], 100 * (i + 1));
    }
    autocompleteReturns(predictions);

    assertThat(service.nearby(LAT, LNG, "cây xăng", 5)).hasSize(5);
  }

  @Test
  @DisplayName("UTC013 radius=1 → bỏ điểm cách 1.600 m, giữ điểm cách 1.400 m")
  void nearby_filtersOutsideRadius() throws Exception {
    autocompleteReturns(prediction("in"), prediction("out"));
    detail("in", "Trong bán kính", 1400);
    detail("out", "Ngoài bán kính", 1600);

    List<NearbyPlace> result = service.nearby(LAT, LNG, "cây xăng", 1);

    assertThat(result).extracting(NearbyPlace::placeId).containsExactly("in");
  }

  @Test
  @DisplayName("UTC014 Goong không có gợi ý → danh sách rỗng")
  void nearby_empty() throws Exception {
    autocompleteReturns();

    assertThat(service.nearby(LAT, LNG, "zzzz", 5)).isEmpty();
  }

  @Test
  @DisplayName("UTC015 Gợi ý thiếu place_id → bỏ qua, giữ các gợi ý còn lại")
  void nearby_skipsBlankPlaceId() throws Exception {
    autocompleteReturns("{\"place_id\":\"\"}", prediction("ok"));
    detail("ok", "Xăng OK", 400);

    List<NearbyPlace> result = service.nearby(LAT, LNG, "cây xăng", 5);

    assertThat(result).extracting(NearbyPlace::placeId).containsExactly("ok");
  }

  @Test
  @DisplayName("UTC016 Chi tiết của 1 gợi ý bị lỗi (404) → bỏ qua, không làm hỏng danh sách")
  void nearby_skipsFailedDetail() throws Exception {
    autocompleteReturns(prediction("bad"), prediction("ok"));
    when(goong.get(eq("/Place/Detail"), argThat(p -> p != null && "bad".equals(p.get("place_id")))))
        .thenReturn(json("{\"result\":{}}"));
    detail("ok", "Xăng OK", 400);

    List<NearbyPlace> result = service.nearby(LAT, LNG, "cây xăng", 5);

    assertThat(result).extracting(NearbyPlace::placeId).containsExactly("ok");
  }

  // ---- directions: alternatives, vehicle, steps ----

  private static String route(long meters, long seconds, String polyline) {
    return "{\"overview_polyline\":{\"points\":\""
        + polyline
        + "\"},\"legs\":[{\"distance\":{\"value\":"
        + meters
        + ",\"text\":\""
        + meters
        + " m\"},\"duration\":{\"value\":"
        + seconds
        + ",\"text\":\""
        + seconds
        + " giây\"},\"steps\":[]}]}";
  }

  @Test
  @DisplayName("UTC024 Goong trả 3 tuyến → tuyến chính + 2 tuyến thay thế")
  void directions_withAlternatives() throws Exception {
    when(goong.get(eq("/Direction"), any()))
        .thenReturn(
            json(
                "{\"routes\":["
                    + route(3000, 600, "main")
                    + ","
                    + route(3500, 540, "alt1")
                    + ","
                    + route(4200, 700, "alt2")
                    + "]}"));

    RouteResponse response = service.directions(16.05, 108.2, 16.06, 108.22, "car");

    assertThat(response.polyline()).isEqualTo("main");
    assertThat(response.distanceMeters()).isEqualTo(3000);
    assertThat(response.alternatives()).hasSize(2);
    assertThat(response.alternatives().get(0).polyline()).isEqualTo("alt1");
    assertThat(response.alternatives().get(0).distanceMeters()).isEqualTo(3500);
    assertThat(response.alternatives().get(1).durationSeconds()).isEqualTo(700);
  }

  @Test
  @DisplayName("UTC025 Goong trả đúng 1 tuyến → alternatives rỗng")
  void directions_singleRoute() throws Exception {
    when(goong.get(eq("/Direction"), any()))
        .thenReturn(json("{\"routes\":[" + route(3000, 600, "main") + "]}"));

    assertThat(service.directions(16.05, 108.2, 16.06, 108.22, "car").alternatives()).isEmpty();
  }

  @Test
  @DisplayName("UTC026 vehicle=bike → gọi Goong với vehicle=bike và alternatives=true")
  void directions_sendsVehicle() throws Exception {
    when(goong.get(eq("/Direction"), any()))
        .thenReturn(json("{\"routes\":[" + route(3000, 600, "main") + "]}"));

    service.directions(16.05, 108.2, 16.06, 108.22, "BIKE");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
    verify(goong).get(eq("/Direction"), params.capture());
    assertThat(params.getValue())
        .containsEntry("vehicle", "bike")
        .containsEntry("alternatives", "true");
  }

  @Test
  @DisplayName("UTC027 Chỉ dẫn HTML → văn bản thường, giữ maneuver và khoảng cách")
  void directions_stepsArePlainText() throws Exception {
    when(goong.get(eq("/Direction"), any()))
        .thenReturn(
            json(
                "{\"routes\":[{\"overview_polyline\":{\"points\":\"p\"},\"legs\":[{"
                    + "\"distance\":{\"value\":300},\"duration\":{\"value\":60},\"steps\":[{"
                    + "\"html_instructions\":\"Rẽ <b>phải</b> vào Nguyễn Văn Linh\","
                    + "\"maneuver\":\"turn-right\",\"distance\":{\"value\":300,\"text\":\"300 m\"},"
                    + "\"duration\":{\"value\":60},"
                    + "\"start_location\":{\"lat\":16.05,\"lng\":108.2},"
                    + "\"end_location\":{\"lat\":16.051,\"lng\":108.201}}]}]}]}"));

    var step = service.directions(16.05, 108.2, 16.06, 108.22, "car").steps().get(0);

    assertThat(step.instruction()).isEqualTo("Rẽ phải vào Nguyễn Văn Linh");
    assertThat(step.maneuver()).isEqualTo("turn-right");
    assertThat(step.distanceMeters()).isEqualTo(300);
  }

  @Test
  @DisplayName("UTC028 Step thiếu start/end location → bỏ step đó, giữ 2 step còn lại")
  void directions_skipsInvalidStep() throws Exception {
    String good =
        "{\"html_instructions\":\"Đi thẳng\",\"distance\":{\"value\":100},\"duration\":{\"value\":20},"
            + "\"start_location\":{\"lat\":16.05,\"lng\":108.2},\"end_location\":{\"lat\":16.051,\"lng\":108.2}}";
    String bad = "{\"html_instructions\":\"Thiếu tọa độ\",\"distance\":{\"value\":50}}";
    when(goong.get(eq("/Direction"), any()))
        .thenReturn(
            json(
                "{\"routes\":[{\"overview_polyline\":{\"points\":\"p\"},\"legs\":[{"
                    + "\"distance\":{\"value\":250},\"duration\":{\"value\":60},\"steps\":["
                    + good
                    + ","
                    + bad
                    + ","
                    + good
                    + "]}]}]}"));

    assertThat(service.directions(16.05, 108.2, 16.06, 108.22, "car").steps()).hasSize(2);
  }
}
