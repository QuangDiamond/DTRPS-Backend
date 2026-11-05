package com.dtrps.modules.media;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.dtrps.modules.media.service.ImageStorage;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

class ImageUploadApiTest extends ApiTestBase {
  private static final String UPLOAD = "/api/uploads/images";
  private static final byte[] PNG = {
    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D
  };

  @MockitoBean private ImageStorage storage;

  private ResultActions upload(String token, MockMultipartFile file) throws Exception {
    return mockMvc.perform(multipart(UPLOAD).file(file).header("Authorization", "Bearer " + token));
  }

  @Test
  @DisplayName("a real png is stored and its URL returned")
  void upload_returnsUrl() throws Exception {
    when(storage.store(any(), eq("image/png"), any(), any()))
        .thenReturn("https://res.cloudinary.com/demo/image/upload/a.png");

    upload(adminToken(), new MockMultipartFile("file", "a.png", "image/png", PNG))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.url").value("https://res.cloudinary.com/demo/image/upload/a.png"));
  }

  @Test
  @DisplayName("a file that only claims to be an image is rejected by its bytes")
  void upload_rejectsFakeImage() throws Exception {
    upload(
            adminToken(),
            new MockMultipartFile("file", "x.jpg", "image/jpeg", "not an image".getBytes()))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.message").value("Chỉ nhận ảnh jpg, png hoặc webp"));
    verify(storage, never()).store(any(), any(), any(), any());
  }

  @Test
  @DisplayName("images over 5MB are rejected")
  void upload_rejectsLargeFile() throws Exception {
    byte[] big = Arrays.copyOf(PNG, 5 * 1024 * 1024 + 1);
    upload(adminToken(), new MockMultipartFile("file", "big.png", "image/png", big))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.message").value("Ảnh tối đa 5MB"));
  }

  @Test
  @DisplayName("travellers cannot upload")
  void upload_forbiddenForTraveller() throws Exception {
    upload(travellerToken(), new MockMultipartFile("file", "a.png", "image/png", PNG))
        .andExpect(status().isForbidden());
  }
}
