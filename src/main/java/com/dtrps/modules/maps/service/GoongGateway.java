package com.dtrps.modules.maps.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Raw access to the Goong REST API; tests replace it. */
public interface GoongGateway {
  /**
   * GET {@code path} with the given query parameters (the API key is added) and return the JSON.
   */
  JsonNode get(String path, Map<String, String> params);
}
