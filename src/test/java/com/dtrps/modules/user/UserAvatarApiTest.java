package com.dtrps.modules.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.dtrps.modules.media.service.ImageStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

class UserAvatarApiTest extends ApiTestBase {
  private static final String AVATAR = "/api/users/me/avatar";
  private static final String URL = "https://res.cloudinary.com/demo/image/upload/avatar.png";
  private static final byte[] PNG = {
    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D
  };

  @MockitoBean private ImageStorage storage;

  private ResultActions upload(String token, byte[] content) throws Exception {
    return mockMvc.perform(
        multipart(AVATAR)
            .file(new MockMultipartFile("file", "me.png", "image/png", content))
            .header("Authorization", "Bearer " + token));
  }

  @Test
  @DisplayName("a traveller uploads a profile picture into the avatar folder; it is saved at once")
  void traveller_changesAvatar() throws Exception {
    when(storage.store(any(), eq("image/png"), any(), eq("dtrps/avatars"))).thenReturn(URL);
    String token = travellerToken();

    upload(token, PNG).andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value(URL));
    getAs("/api/users/me", token).andExpect(jsonPath("$.avatarUrl").value(URL));

    deleteAs(AVATAR, token).andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").isEmpty());
    getAs("/api/users/me", token).andExpect(jsonPath("$.avatarUrl").isEmpty());
  }

  @Test
  @DisplayName("only real images are accepted; signing in is required")
  void rejectsFakeImage_andAnonymous() throws Exception {
    upload(travellerToken(), "not an image".getBytes())
        .andExpect(status().isUnsupportedMediaType());
    mockMvc
        .perform(multipart(AVATAR).file(new MockMultipartFile("file", "me.png", "image/png", PNG)))
        .andExpect(status().isUnauthorized());
  }
}
