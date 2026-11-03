package com.dtrps.modules.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@TestPropertySource(properties = "app.auth.max-failed-logins=3")
class LoginThrottleTest extends IntegrationTestBase {
  private static final String PASSWORD = "Traveller123";
  private static final String WRONG_PASSWORD = "Wrong12345";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("too many failures block the email, even with the right password")
  void login_tooManyFailures_blocksThatEmailOnly() throws Exception {
    String email = registeredEmail();
    String otherEmail = registeredEmail();
    for (int attempt = 0; attempt < 3; attempt++) {
      login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
    }

    login(email, PASSWORD)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.message").isNotEmpty());

    login(otherEmail, PASSWORD).andExpect(status().isOk());
  }

  @Test
  @DisplayName("an unknown email is blocked the same way, so blocking reveals nothing")
  void login_unknownEmail_isBlockedToo() throws Exception {
    String email = "nobody-" + UUID.randomUUID() + "@dtrps.test";
    for (int attempt = 0; attempt < 3; attempt++) {
      login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
    }

    login(email, WRONG_PASSWORD).andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("a successful login clears earlier failures")
  void login_success_resetsFailures() throws Exception {
    String email = registeredEmail();
    login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
    login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());

    login(email, PASSWORD).andExpect(status().isOk());

    login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
    login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
    login(email, PASSWORD).andExpect(status().isOk());
  }

  private String registeredEmail() throws Exception {
    String email = "user-" + UUID.randomUUID() + "@dtrps.test";
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "Nam", "email", email, "password", PASSWORD))))
        .andExpect(status().isCreated());
    return email;
  }

  private ResultActions login(String email, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
  }
}
