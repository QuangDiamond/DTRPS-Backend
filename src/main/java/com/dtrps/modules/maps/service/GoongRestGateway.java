package com.dtrps.modules.maps.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.maps.config.GoongProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

@Service
public class GoongRestGateway implements GoongGateway {
  private static final Logger log = LoggerFactory.getLogger(GoongRestGateway.class);
  private static final String DEFAULT_BASE_URL = "https://rsapi.goong.io";
  static final String NOT_CONFIGURED = "Dịch vụ bản đồ chưa được cấu hình (thiếu GOONG_API_KEY)";
  static final String UNAVAILABLE = "Không gọi được dịch vụ bản đồ, vui lòng thử lại sau";

  private final GoongProperties properties;
  private final RestClient restClient;

  public GoongRestGateway(GoongProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(10));
    String baseUrl =
        properties.baseUrl() == null || properties.baseUrl().isBlank()
            ? DEFAULT_BASE_URL
            : properties.baseUrl();
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }

  @Override
  public JsonNode get(String path, Map<String, String> params) {
    if (!properties.configured()) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, NOT_CONFIGURED);
    }
    try {
      return restClient
          .get()
          .uri(
              builder -> {
                UriBuilder uri = builder.path(path);
                params.forEach(uri::queryParam);
                return uri.queryParam("api_key", properties.apiKey()).build();
              })
          .retrieve()
          .body(JsonNode.class);
    } catch (RestClientException e) {
      // Never log the URL: it carries the API key
      log.warn("Goong request to {} failed: {}", path, e.getClass().getSimpleName());
      throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
    }
  }
}
