package com.dtrps.modules.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.media.config.CloudinaryProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit test CloudinaryImageStorage — sheet Media, UTC001–UTC003 trong Report5.1. Cloudinary được
 * giả bằng MockRestServiceServer, không tải ảnh lên thật.
 */
class CloudinaryImageStorageTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-19T03:00:00Z"), ZoneOffset.UTC);
  private static final String UPLOAD = "https://api.cloudinary.com/v1_1/demo/image/upload";
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};

  private static CloudinaryImageStorage storage(String url) {
    return new CloudinaryImageStorage(new CloudinaryProperties(url, null, null), CLOCK);
  }

  private static MockRestServiceServer bind(CloudinaryImageStorage storage) {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    ReflectionTestUtils.setField(storage, "restClient", builder.build());
    return server;
  }

  @Test
  @DisplayName("UTC001 CLOUDINARY_URL set → signed upload, https URL returned")
  void configured_returnsUrl() {
    CloudinaryImageStorage storage = storage("cloudinary://key:secret@demo");
    MockRestServiceServer server = bind(storage);
    server
        .expect(requestTo(UPLOAD))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            request -> {
              String body = request.getBody().toString();
              assertThat(body).isNotNull();
            })
        .andRespond(
            withSuccess(
                "{\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/a.png\"}",
                MediaType.APPLICATION_JSON));

    String url = storage.store(PNG, "image/png", "upload.png");

    assertThat(url).isEqualTo("https://res.cloudinary.com/demo/image/upload/a.png");
    server.verify();
  }

  @Test
  @DisplayName("UTC002 CLOUDINARY_URL missing (or malformed) → 503")
  void missingUrl_unavailable() {
    for (String url : new String[] {null, "not-a-url"}) {
      ApiException e =
          catchThrowableOfType(
              () -> storage(url).store(PNG, "image/png", "upload.png"), ApiException.class);

      assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
      assertThat(e.getMessage()).isEqualTo("Máy chủ chưa cấu hình lưu ảnh (CLOUDINARY_URL)");
    }
  }

  @Test
  @DisplayName("UTC003 Cloudinary answers an error → 502")
  void cloudinaryError_badGateway() {
    CloudinaryImageStorage storage = storage("cloudinary://key:secret@demo");
    MockRestServiceServer server = bind(storage);
    server.expect(requestTo(UPLOAD)).andRespond(withServerError());

    ApiException e =
        catchThrowableOfType(
            () -> storage.store(PNG, "image/png", "upload.png"), ApiException.class);

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(e.getMessage()).isEqualTo("Không tải được ảnh lên, vui lòng thử lại");
  }
}
