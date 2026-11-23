package com.dtrps.modules.maps.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.maps.dto.AddressSuggestion;
import com.dtrps.modules.maps.dto.GeoLocation;
import com.dtrps.modules.maps.dto.NearbyPlace;
import com.dtrps.modules.maps.dto.RouteResponse;
import com.dtrps.modules.maps.dto.RouteStep;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Address search, geocoding and directions through Goong. Results are trimmed to what the clients
 * use, so a change in Goong's payload stays inside this class.
 */
@Service
public class MapService {
  private static final int SUGGESTION_LIMIT = 6;
  private static final int NEARBY_LIMIT = 5;
  private static final String PLACE_NOT_FOUND = "Không tìm thấy địa điểm trên bản đồ";
  private static final String ADDRESS_NOT_FOUND = "Không tìm thấy địa chỉ cho vị trí này";
  private static final String NO_ROUTE = "Không tìm được đường đi giữa hai điểm";

  private final GoongGateway goong;

  public MapService(GoongGateway goong) {
    this.goong = goong;
  }

  /** Suggestions for what the user typed; {@code near} (lat,lng) ranks nearby results first. */
  public List<AddressSuggestion> autocomplete(String input, Double latitude, Double longitude) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("input", input.trim());
    params.put("limit", String.valueOf(SUGGESTION_LIMIT));
    if (latitude != null && longitude != null) {
      params.put("location", point(latitude, longitude));
    }
    List<AddressSuggestion> suggestions = new ArrayList<>();
    for (JsonNode prediction : goong.get("/Place/AutoComplete", params).path("predictions")) {
      JsonNode format = prediction.path("structured_formatting");
      String description = prediction.path("description").asText("");
      suggestions.add(
          new AddressSuggestion(
              prediction.path("place_id").asText(),
              format.path("main_text").asText(description),
              format.path("secondary_text").asText(""),
              description));
    }
    return suggestions;
  }

  /** Coordinates and address of a search result. */
  public GeoLocation place(String placeId) {
    JsonNode result = goong.get("/Place/Detail", Map.of("place_id", placeId)).path("result");
    if (result.isMissingNode() || result.path("geometry").path("location").isMissingNode()) {
      throw new ApiException(HttpStatus.NOT_FOUND, PLACE_NOT_FOUND);
    }
    return location(result, placeId);
  }

  /** The address at a point (the closest match Goong knows). */
  public GeoLocation reverse(double latitude, double longitude) {
    JsonNode results =
        goong.get("/Geocode", Map.of("latlng", point(latitude, longitude))).path("results");
    if (!results.isArray() || results.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, ADDRESS_NOT_FOUND);
    }
    GeoLocation found = location(results.get(0), null);
    // Keep the exact point the user chose, only the address comes from Goong
    return new GeoLocation(found.placeId(), found.name(), found.address(), latitude, longitude);
  }

  /** Tìm các địa điểm tiện ích (cây xăng, ATM, quán ăn...) gần vị trí hiện tại qua Goong. */
  public List<NearbyPlace> nearby(
      double latitude, double longitude, String keyword, int radiusKm) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("input", keyword.trim());
    params.put("location", point(latitude, longitude));
    params.put("radius", String.valueOf(radiusKm));
    params.put("limit", String.valueOf(NEARBY_LIMIT));
    params.put("more_compound", "true");

    JsonNode predictions = goong.get("/Place/AutoComplete", params).path("predictions");
    List<NearbyPlace> results = new ArrayList<>();
    for (JsonNode prediction : predictions) {
      if (results.size() >= NEARBY_LIMIT) {
        break;
      }
      String placeId = prediction.path("place_id").asText("");
      if (placeId.isBlank()) {
        continue;
      }
      try {
        GeoLocation detail = place(placeId);
        long meters =
            Math.round(GeoDistance.meters(latitude, longitude, detail.latitude(), detail.longitude()));
        // Lọc bớt kết quả ở quá xa ngoài bán kính yêu cầu (cộng thêm biên độ nhỏ cho Goong)
        if (meters > radiusKm * 1500L) {
          continue;
        }
        String name =
            detail.name() != null && !detail.name().isBlank()
                ? detail.name()
                : prediction.path("structured_formatting").path("main_text").asText(detail.address());
        results.add(
            new NearbyPlace(
                placeId, name, detail.address(), detail.latitude(), detail.longitude(), meters));
      } catch (ApiException ignored) {
        // Bỏ qua 1 điểm lỗi chi tiết để không làm hỏng cả danh sách lân cận
      }
    }
    results.sort(Comparator.comparingLong(NearbyPlace::distanceMeters));
    return results;
  }

  /** Best route (+ alternative routes when Goong returns them); {@code vehicle} is car/bike/taxi/truck/hd. */
  public RouteResponse directions(
      double fromLatitude,
      double fromLongitude,
      double toLatitude,
      double toLongitude,
      String vehicle) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("origin", point(fromLatitude, fromLongitude));
    params.put("destination", point(toLatitude, toLongitude));
    params.put("vehicle", vehicle.toLowerCase(Locale.ROOT));
    params.put("alternatives", "true");
    JsonNode routes = goong.get("/Direction", params).path("routes");
    if (!routes.isArray() || routes.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, NO_ROUTE);
    }
    RouteResponse primary = parseRoute(routes.get(0), List.of());
    List<RouteResponse> alternatives = new ArrayList<>();
    for (int i = 1; i < routes.size(); i++) {
      alternatives.add(parseRoute(routes.get(i), List.of()));
    }
    return new RouteResponse(
        primary.distanceMeters(),
        primary.durationSeconds(),
        primary.distanceText(),
        primary.durationText(),
        primary.polyline(),
        primary.steps(),
        alternatives);
  }

  private static RouteResponse parseRoute(JsonNode route, List<RouteResponse> alternatives) {
    long meters = 0;
    long seconds = 0;
    List<RouteStep> steps = new ArrayList<>();
    for (JsonNode leg : route.path("legs")) {
      meters += leg.path("distance").path("value").asLong();
      seconds += leg.path("duration").path("value").asLong();
      for (JsonNode step : leg.path("steps")) {
        RouteStep parsed = parseStep(step);
        if (parsed != null) {
          steps.add(parsed);
        }
      }
    }
    JsonNode firstLeg = route.path("legs").path(0);
    return new RouteResponse(
        meters,
        seconds,
        firstLeg.path("distance").path("text").asText(null),
        firstLeg.path("duration").path("text").asText(null),
        route.path("overview_polyline").path("points").asText(""),
        steps,
        alternatives);
  }

  /** One Goong step, or null when it has no usable start/end point. */
  private static RouteStep parseStep(JsonNode step) {
    JsonNode start = step.path("start_location");
    JsonNode end = step.path("end_location");
    if (!start.hasNonNull("lat") || !start.hasNonNull("lng")
        || !end.hasNonNull("lat") || !end.hasNonNull("lng")) {
      return null;
    }
    return new RouteStep(
        plainText(step.path("html_instructions").asText("")),
        step.path("maneuver").asText(null),
        step.path("distance").path("value").asLong(),
        step.path("duration").path("value").asLong(),
        step.path("distance").path("text").asText(null),
        start.path("lat").asDouble(),
        start.path("lng").asDouble(),
        end.path("lat").asDouble(),
        end.path("lng").asDouble());
  }

  /** Goong sends instructions as HTML ("Rẽ <b>phải</b> vào ..."); the clients show plain text. */
  static String plainText(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    return html
        .replaceAll("(?i)<(div|br|p)[^>]*>", " ")
        .replaceAll("<[^>]+>", "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static GeoLocation location(JsonNode node, String fallbackPlaceId) {
    JsonNode point = node.path("geometry").path("location");
    String address = node.path("formatted_address").asText("");
    return new GeoLocation(
        node.path("place_id").asText(fallbackPlaceId),
        node.path("name").asText(address),
        address,
        point.path("lat").asDouble(),
        point.path("lng").asDouble());
  }

  private static String point(double latitude, double longitude) {
    return latitude + "," + longitude;
  }
}
