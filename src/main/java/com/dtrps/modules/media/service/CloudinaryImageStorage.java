package com.dtrps.modules.media.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.media.config.CloudinaryProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Signed upload to Cloudinary's REST API (no SDK needed). The secret never leaves the server; the
 * client only gets the resulting {@code secure_url}.
 */
@Service
public class CloudinaryImageStorage implements ImageStorage {
  private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorage.class);
  private static final String DEFAULT_FOLDER = "dtrps/places";
  static final String NOT_CONFIGURED = "Máy chủ chưa cấu hình lưu ảnh (CLOUDINARY_URL)";
  static final String UPLOAD_FAILED = "Không tải được ảnh lên, vui lòng thử lại";

  private record Account(String apiKey, String apiSecret, String cloudName) {}

  private final Account account;
  private final String folder;
  private final Clock clock;
  private final RestClient restClient = RestClient.create();

  public CloudinaryImageStorage(CloudinaryProperties properties, Clock clock) {
    this.account = parse(properties.url());
    this.folder =
        properties.folder() == null || properties.folder().isBlank()
            ? DEFAULT_FOLDER
            : properties.folder().trim();
    this.clock = clock;
  }

  /** {@code cloudinary://key:secret@cloud} → parts; null when not set or malformed. */
  private static Account parse(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(url.trim());
      String[] credentials = uri.getUserInfo().split(":", 2);
      if (!"cloudinary".equals(uri.getScheme()) || credentials.length != 2) {
        throw new IllegalArgumentException("Unexpected format");
      }
      return new Account(credentials[0], credentials[1], uri.getHost());
    } catch (RuntimeException ex) {
      log.warn("CLOUDINARY_URL is malformed; image uploads are disabled");
      return null;
    }
  }

  @Override
  public String store(byte[] content, String contentType, String filename) {
    return store(content, contentType, filename, null);
  }

  @Override
  public String store(byte[] content, String contentType, String filename, String target) {
    String folder = target == null || target.isBlank() ? this.folder : target.trim();
    if (account == null) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, NOT_CONFIGURED);
    }
    String timestamp = String.valueOf(clock.instant().getEpochSecond());
    // Signed params sorted by name, then the secret appended (Cloudinary signature rules)
    String signature = sha1("folder=" + folder + "&timestamp=" + timestamp + account.apiSecret());

    HttpHeaders fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(MediaType.parseMediaType(contentType));
    ByteArrayResource file =
        new ByteArrayResource(content) {
          @Override
          public String getFilename() {
            return filename;
          }
        };
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", new HttpEntity<>(file, fileHeaders));
    body.add("api_key", account.apiKey());
    body.add("timestamp", timestamp);
    body.add("folder", folder);
    body.add("signature", signature);

    try {
      Map<String, Object> response =
          restClient
              .post()
              .uri("https://api.cloudinary.com/v1_1/{cloud}/image/upload", account.cloudName())
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(body)
              .retrieve()
              .body(new ParameterizedTypeReference<>() {});
      Object url = response == null ? null : response.get("secure_url");
      if (url == null) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, UPLOAD_FAILED);
      }
      return url.toString();
    } catch (RestClientException ex) {
      log.warn("Cloudinary upload failed: {}", ex.getMessage());
      throw new ApiException(HttpStatus.BAD_GATEWAY, UPLOAD_FAILED);
    }
  }

  private static String sha1(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
