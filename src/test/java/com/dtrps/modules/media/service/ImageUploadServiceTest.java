package com.dtrps.modules.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.SystemSettingsService;
import com.dtrps.modules.media.config.CloudinaryProperties;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

/** Unit test ImageUploadService — sheet Media, UTC004–UTC010 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {
  private static final long FIVE_MB = 5L * 1024 * 1024;
  private static final String URL = "https://res.cloudinary.com/demo/image/upload/a.png";

  @Mock private ImageStorage storage;
  @Mock private SystemSettingsService settings;

  private ImageUploadService service;

  @BeforeEach
  void setUp() {
    service = new ImageUploadService(storage, new CloudinaryProperties(null, null, null), settings);
    lenient().when(settings.maxImageBytes()).thenReturn(FIVE_MB);
    lenient().when(settings.maxImageMb()).thenReturn(5);
    lenient().when(storage.store(any(), anyString(), anyString(), any())).thenReturn(URL);
  }

  /** File {@code size} byte bắt đầu bằng chữ ký {@code header} */
  private static MockMultipartFile file(String name, byte[] header, long size) {
    byte[] content = Arrays.copyOf(header, (int) size);
    return new MockMultipartFile("file", name, "application/octet-stream", content);
  }

  private static byte[] png() {
    return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  }

  private static byte[] jpg() {
    return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
  }

  private ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  @Nested
  @DisplayName("upload")
  class Upload {
    @Test
    @DisplayName("UTC004 Real 200 KB png → stored as png in the default folder")
    void png_stored() {
      assertThat(service.upload(file("a.png", png(), 200 * 1024))).isEqualTo(URL);

      verify(storage).store(any(), eq("image/png"), eq("upload.png"), isNull());
    }

    @Test
    @DisplayName("UTC005 Empty file → 400")
    void empty_badRequest() {
      ApiException e = error(() -> service.upload(file("a.png", new byte[0], 0)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Vui lòng chọn ảnh");
    }

    @Test
    @DisplayName("UTC006 .txt file renamed to .jpg → 415 (checked by bytes, not the name)")
    void fakeJpg_unsupported() {
      MockMultipartFile fake =
          new MockMultipartFile(
              "file", "a.jpg", "image/jpeg", "hello world".getBytes(StandardCharsets.UTF_8));

      ApiException e = error(() -> service.upload(fake));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
      assertThat(e.getMessage()).isEqualTo("Chỉ nhận ảnh jpg, png hoặc webp");
      verify(storage, never()).store(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("UTC007 File of exactly 5 MB → accepted")
    void exactlyLimit_stored() {
      assertThat(service.upload(file("a.png", png(), FIVE_MB))).isEqualTo(URL);
    }

    @Test
    @DisplayName("UTC008 File of 5 MB + 1 byte → 413 with the admin's limit")
    void overLimit_tooLarge() {
      ApiException e = error(() -> service.upload(file("a.png", png(), FIVE_MB + 1)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
      assertThat(e.getMessage()).isEqualTo("Ảnh tối đa 5MB");
    }
  }

  @Nested
  @DisplayName("uploadAvatar")
  class UploadAvatar {
    @Test
    @DisplayName("UTC009 100 KB jpg → stored in the avatar folder")
    void jpg_avatarFolder() {
      service.uploadAvatar(file("me.jpg", jpg(), 100 * 1024));

      verify(storage).store(any(), eq("image/jpeg"), eq("upload.jpg"), eq("dtrps/avatars"));
    }

    @Test
    @DisplayName("UTC010 gif file → 415")
    void gif_unsupported() {
      byte[] gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);

      ApiException e = error(() -> service.uploadAvatar(file("me.gif", gif, 1024)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
      assertThat(e.getMessage()).isEqualTo("Chỉ nhận ảnh jpg, png hoặc webp");
    }
  }
}
