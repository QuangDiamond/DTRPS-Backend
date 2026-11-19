package com.dtrps.modules.maps.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.maps.dto.GeoLocation;
import com.dtrps.modules.maps.dto.RouteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Unit test MapService — sheet Maps, UTC004–UTC009 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class MapServiceTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  @Mock private GoongGateway goong;

  private MapService service;

  @BeforeEach
  void setUp() {
    service = new MapService(goong);
  }

  private void goongAnswers(String path, String json) throws Exception {
    JsonNode body = JSON.readTree(json);
    when(goong.get(eq(path), any())).thenReturn(body);
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  @Test
  @DisplayName("UTC004 Valid Goong placeId → coordinates and address")
  void place_found() throws Exception {
    goongAnswers(
        "/Place/Detail",
        "{\"result\":{\"place_id\":\"p1\",\"name\":\"Cầu Rồng\","
            + "\"formatted_address\":\"Cầu Rồng, Đà Nẵng\","
            + "\"geometry\":{\"location\":{\"lat\":16.061,\"lng\":108.227}}}}");

    GeoLocation location = service.place("p1");

    assertThat(location.name()).isEqualTo("Cầu Rồng");
    assertThat(location.address()).isEqualTo("Cầu Rồng, Đà Nẵng");
    assertThat(location.latitude()).isEqualTo(16.061);
  }

  @Test
  @DisplayName("UTC005 Goong returns an empty result → 404")
  void place_missing() throws Exception {
    goongAnswers("/Place/Detail", "{\"result\":{}}");

    ApiException e = error(() -> service.place("abc"));

    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(e.getMessage()).isEqualTo("Không tìm thấy địa điểm trên bản đồ");
  }

  @Test
  @DisplayName("UTC006 Valid point → address from Goong, the exact point is kept")
  void reverse_found() throws Exception {
    goongAnswers(
        "/Geocode",
        "{\"results\":[{\"formatted_address\":\"12 Bạch Đằng, Hải Châu, Đà Nẵng\","
            + "\"geometry\":{\"location\":{\"lat\":16.06,\"lng\":108.22}}}]}");

    GeoLocation location = service.reverse(16.0544, 108.2022);

    assertThat(location.address()).isEqualTo("12 Bạch Đằng, Hải Châu, Đà Nẵng");
    assertThat(location.latitude()).isEqualTo(16.0544);
    assertThat(location.longitude()).isEqualTo(108.2022);
  }

  @Test
  @DisplayName("UTC007 Goong has no address for the point → 404")
  void reverse_missing() throws Exception {
    goongAnswers("/Geocode", "{\"results\":[]}");

    ApiException e = error(() -> service.reverse(0, 0));

    assertThat(e.getMessage()).isEqualTo("Không tìm thấy địa chỉ cho vị trí này");
  }

  @Test
  @DisplayName("UTC008 Route with 2 legs → total distance and duration of all legs")
  void directions_sumsLegs() throws Exception {
    goongAnswers(
        "/Direction",
        "{\"routes\":[{\"overview_polyline\":{\"points\":\"abc\"},\"legs\":["
            + "{\"distance\":{\"value\":1200,\"text\":\"1.2 km\"},\"duration\":{\"value\":300,\"text\":\"5 phút\"}},"
            + "{\"distance\":{\"value\":800},\"duration\":{\"value\":200}}]}]}");

    RouteResponse route = service.directions(16.05, 108.2, 16.06, 108.22, "CAR");

    assertThat(route.distanceMeters()).isEqualTo(2000);
    assertThat(route.durationSeconds()).isEqualTo(500);
    assertThat(route.polyline()).isEqualTo("abc");
  }

  @Test
  @DisplayName("UTC009 Goong has no route → 404")
  void directions_noRoute() throws Exception {
    goongAnswers("/Direction", "{\"routes\":[]}");

    ApiException e = error(() -> service.directions(16.0, 108.0, 10.8, 106.7, "car"));

    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(e.getMessage()).isEqualTo("Không tìm được đường đi giữa hai điểm");
  }
}
