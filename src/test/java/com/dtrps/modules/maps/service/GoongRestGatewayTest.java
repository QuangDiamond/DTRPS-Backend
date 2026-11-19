package com.dtrps.modules.maps.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.maps.config.GoongProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit test GoongRestGateway — sheet Maps, UTC001–UTC003 trong Report5.1. Goong được giả bằng
 * MockRestServiceServer (gắn vào RestClient của gateway), không gọi mạng thật.
 */
class GoongRestGatewayTest {
  private static final String BASE = "https://rsapi.goong.io";

  /** Gateway có RestClient nối vào server giả */
  private static MockRestServiceServer bind(GoongRestGateway gateway) {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    ReflectionTestUtils.setField(gateway, "restClient", builder.build());
    return server;
  }

  @Test
  @DisplayName("UTC001 Key set, Goong answers 200 → JSON returned, key sent as api_key")
  void configured_returnsJson() {
    GoongRestGateway gateway = new GoongRestGateway(new GoongProperties("goong-key", null));
    MockRestServiceServer server = bind(gateway);
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/Place/AutoComplete")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("input", "CauRong"))
        .andExpect(queryParam("api_key", "goong-key"))
        .andRespond(
            withSuccess("{\"predictions\":[{\"place_id\":\"p1\"}]}", MediaType.APPLICATION_JSON));

    JsonNode body = gateway.get("/Place/AutoComplete", Map.of("input", "CauRong"));

    assertThat(body.path("predictions").get(0).path("place_id").asText()).isEqualTo("p1");
    server.verify();
  }

  @Test
  @DisplayName("UTC002 GOONG_API_KEY missing → 503, nothing sent")
  void missingKey_unavailable() {
    GoongRestGateway gateway = new GoongRestGateway(new GoongProperties(" ", null));
    MockRestServiceServer server = bind(gateway);

    ApiException e =
        catchThrowableOfType(() -> gateway.get("/Geocode", Map.of()), ApiException.class);

    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(e.getMessage()).isEqualTo("Dịch vụ bản đồ chưa được cấu hình (thiếu GOONG_API_KEY)");
    server.verify();
  }

  @Test
  @DisplayName("UTC003 Network error / timeout → 502")
  void networkError_badGateway() {
    GoongRestGateway gateway = new GoongRestGateway(new GoongProperties("goong-key", null));
    MockRestServiceServer server = bind(gateway);
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/Geocode")))
        .andRespond(withException(new IOException("timeout")));

    ApiException e =
        catchThrowableOfType(
            () -> gateway.get("/Geocode", Map.of("latlng", "16.05,108.2")), ApiException.class);

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(e.getMessage()).isEqualTo("Không gọi được dịch vụ bản đồ, vui lòng thử lại sau");
  }
}
