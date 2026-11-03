package com.dtrps.modules.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.IntegrationTestBase;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@TestPropertySource(properties = "app.auth.rate-limit-per-minute=3")
class AuthRateLimitTest extends IntegrationTestBase {
  private static final String CREDENTIALS =
      "{\"email\":\"nobody@dtrps.test\",\"password\":\"Wrong1234\"}";

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("login returns 429 with Retry-After once the limit is exceeded")
  void login_overLimit_returnsTooManyRequests() throws Exception {
    for (int attempt = 0; attempt < 3; attempt++) {
      login().andExpect(status().isUnauthorized());
    }

    login()
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andExpect(jsonPath("$.message").isNotEmpty());

    mockMvc
        .perform(
            post(URI.create("/api/auth/%6Cogin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREDENTIALS))
        .andExpect(status().isTooManyRequests());
  }

  private ResultActions login() throws Exception {
    return mockMvc.perform(
        post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS));
  }
}
