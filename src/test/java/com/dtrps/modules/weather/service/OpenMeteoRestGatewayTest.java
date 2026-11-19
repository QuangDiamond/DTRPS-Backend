package com.dtrps.modules.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dtrps.modules.weather.config.WeatherProperties;
import com.dtrps.modules.weather.dto.OpenMeteoDtos;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Exercises the real HTTP call + JSON parsing, against MockRestServiceServer (no real network). */
class OpenMeteoRestGatewayTest {
  private static final String SAMPLE_RESPONSE =
      """
      {
        "daily": {"time": ["2026-11-19"], "weather_code": [61]},
        "hourly": {
          "time": ["2026-11-19T00:00", "2026-11-19T01:00"],
          "weather_code": [0, 61],
          "temperature_2m": [22.5, 21.0],
          "precipitation_probability": [10, 80],
          "precipitation": [0.0, 8.0],
          "wind_speed_10m": [5.0, 6.0]
        }
      }
      """;

  @Test
  void parsesParallelHourlyArraysFromOpenMeteoResponse() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    WeatherProperties properties =
        new WeatherProperties("https://api.open-meteo.com", null, null, null, null, null);

    server
        .expect(
            request -> {
              String uri = request.getURI().toString();
              assertThat(uri).contains("/v1/forecast");
              assertThat(uri).contains("latitude=21.0285");
              assertThat(uri).contains("longitude=105.8542");
              assertThat(uri).contains("timezone=Asia/Bangkok");
              assertThat(uri).contains("forecast_days=7");
            })
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(SAMPLE_RESPONSE, MediaType.APPLICATION_JSON));

    OpenMeteoRestGateway gateway = new OpenMeteoRestGateway(builder, properties);
    OpenMeteoDtos.ForecastResponse response = gateway.forecast(21.0285, 105.8542);

    assertThat(response.hourly().time()).hasSize(2);
    assertThat(response.hourly().weatherCode()).containsExactly(0, 61);
    assertThat(response.hourly().precipitationProbability()).containsExactly(10, 80);
    assertThat(response.hourly().precipitation()).containsExactly(0.0, 8.0);
    server.verify();
  }
}
