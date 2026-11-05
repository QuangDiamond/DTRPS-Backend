package com.dtrps.modules.auth.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.auth.config.GoogleProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks Google about the access token: {@code tokeninfo} proves it is live and was issued to our
 * Client ID (so a token from another app cannot be replayed here), {@code userinfo} gives the name
 * and picture.
 */
@Component
public class GoogleRestVerifier implements GoogleTokenVerifier {
  private static final Logger log = LoggerFactory.getLogger(GoogleRestVerifier.class);
  private static final String TOKEN_INFO = "https://oauth2.googleapis.com/tokeninfo";
  private static final String USER_INFO = "https://openidconnect.googleapis.com/v1/userinfo";
  static final String INVALID = "Phiên đăng nhập Google không hợp lệ, vui lòng thử lại";
  static final String UNAVAILABLE = "Không kết nối được Google, vui lòng thử lại sau";

  private final GoogleProperties properties;
  private final RestClient restClient;

  public GoogleRestVerifier(GoogleProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(10));
    this.restClient = RestClient.builder().requestFactory(factory).build();
  }

  @Override
  public GoogleAccount verify(String accessToken) {
    try {
      JsonNode info =
          restClient
              .get()
              .uri(TOKEN_INFO + "?access_token={token}", accessToken)
              .retrieve()
              .body(JsonNode.class);
      String audience = info == null ? null : info.path("aud").asText(info.path("azp").asText());
      if (audience == null || !audience.equals(properties.clientId())) {
        throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID);
      }
      JsonNode user =
          restClient
              .get()
              .uri(USER_INFO)
              .header("Authorization", "Bearer " + accessToken)
              .retrieve()
              .body(JsonNode.class);
      if (user == null || user.path("email").asText().isBlank()) {
        throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID);
      }
      return new GoogleAccount(
          user.path("email").asText(),
          user.path("email_verified").asBoolean(false),
          user.path("name").asText(null),
          user.path("picture").asText(null));
    } catch (HttpClientErrorException e) {
      // 400/401 từ Google: token sai hoặc đã hết hạn
      throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID);
    } catch (RestClientException e) {
      log.warn("Không gọi được Google để kiểm tra token", e);
      throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
    }
  }
}
