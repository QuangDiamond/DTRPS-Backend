package com.dtrps;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Helpers for API tests: real HTTP layer, real security, real database. */
public abstract class ApiTestBase extends IntegrationTestBase {
  protected static final String PASSWORD = "Traveller123";

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;

  protected static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@dtrps.test";
  }

  /** Registers a new traveller and returns the access token. */
  protected String travellerToken() throws Exception {
    return session(uniqueEmail()).get("accessToken").asText();
  }

  /** Registers a new traveller and returns the whole auth response. */
  protected JsonNode session(String email) throws Exception {
    return json(
        postAs(
            "/api/auth/register",
            null,
            Map.of("name", "Nam", "email", email, "password", PASSWORD)));
  }

  protected String adminToken() throws Exception {
    return login(ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  /** Logs in again, which picks up roles granted after the previous token was issued. */
  protected String login(String email, String password) throws Exception {
    return json(postAs("/api/auth/login", null, Map.of("email", email, "password", password)))
        .get("accessToken")
        .asText();
  }

  protected ResultActions getAs(String path, String token) throws Exception {
    return mockMvc.perform(withToken(get(path), token));
  }

  protected ResultActions postAs(String path, String token, Object body) throws Exception {
    return mockMvc.perform(withBody(post(path), token, body));
  }

  protected ResultActions putAs(String path, String token, Object body) throws Exception {
    return mockMvc.perform(withBody(put(path), token, body));
  }

  protected ResultActions deleteAs(String path, String token) throws Exception {
    return mockMvc.perform(withToken(delete(path), token));
  }

  protected JsonNode json(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private MockHttpServletRequestBuilder withBody(
      MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
    MockHttpServletRequestBuilder builder = withToken(request, token);
    if (body == null) {
      return builder;
    }
    return builder
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  private static MockHttpServletRequestBuilder withToken(
      MockHttpServletRequestBuilder request, String token) {
    return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }
}
