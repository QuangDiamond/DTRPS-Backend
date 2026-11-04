package com.dtrps.modules.maps.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.maps.dto.AddressSuggestion;
import com.dtrps.modules.maps.dto.GeoLocation;
import com.dtrps.modules.maps.dto.RouteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
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

  /** Best route; {@code vehicle} is one of car, bike, taxi, truck, hd (Goong's values). */
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
    JsonNode routes = goong.get("/Direction", params).path("routes");
    if (!routes.isArray() || routes.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, NO_ROUTE);
    }
    JsonNode route = routes.get(0);
    long meters = 0;
    long seconds = 0;
    for (JsonNode leg : route.path("legs")) {
      meters += leg.path("distance").path("value").asLong();
      seconds += leg.path("duration").path("value").asLong();
    }
    JsonNode firstLeg = route.path("legs").path(0);
    return new RouteResponse(
        meters,
        seconds,
        firstLeg.path("distance").path("text").asText(null),
        firstLeg.path("duration").path("text").asText(null),
        route.path("overview_polyline").path("points").asText(""));
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
