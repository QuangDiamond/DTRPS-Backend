package com.dtrps.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.IntegrationTestBase;
import com.dtrps.modules.auth.repository.RefreshTokenRepository;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class AuthFlowTest extends IntegrationTestBase {
  private static final String PASSWORD = "Traveller123";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Test
  @DisplayName("register creates a traveller, returns tokens and never stores the raw password")
  void register_validRequest_returnsTokens() throws Exception {
    String email = uniqueEmail();

    postJson("/api/auth/register", Map.of("name", "Nam", "email", email, "password", PASSWORD))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.user.email").value(email))
        .andExpect(jsonPath("$.user.roles[0]").value("TRAVELLER"))
        .andExpect(jsonPath("$.admin").doesNotExist());

    User saved = userRepository.findByEmail(email).orElseThrow();
    assertThat(saved.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
  }

  @Test
  @DisplayName("register ignores a role sent by the client")
  void register_withRoleField_staysTraveller() throws Exception {
    postJson(
            "/api/auth/register",
            Map.of("name", "Nam", "email", uniqueEmail(), "password", PASSWORD, "roles", "ADMIN"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.roles.length()").value(1))
        .andExpect(jsonPath("$.user.roles[0]").value("TRAVELLER"));
  }

  @Test
  @DisplayName("register rejects an email that differs only by case")
  void register_duplicateEmail_returnsConflict() throws Exception {
    String email = uniqueEmail();
    register(email);

    postJson(
            "/api/auth/register",
            Map.of("name", "Nam", "email", email.toUpperCase(), "password", PASSWORD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Email đã được sử dụng"));
  }

  @Test
  @DisplayName("register rejects a weak password and reports the field")
  void register_weakPassword_returnsFieldError() throws Exception {
    postJson(
            "/api/auth/register", Map.of("name", "Nam", "email", uniqueEmail(), "password", "weak"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").isNotEmpty())
        .andExpect(jsonPath("$.errors.password").isNotEmpty());
  }

  @Test
  @DisplayName("register rejects a password over the bcrypt byte limit")
  void register_passwordOverBcryptLimit_returnsBadRequest() throws Exception {
    postJson(
            "/api/auth/register",
            Map.of("name", "Nam", "email", uniqueEmail(), "password", "Aa1" + "ệ".repeat(30)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("login returns the same 401 for a wrong password and an unknown email")
  void login_badCredentials_returnsSameError() throws Exception {
    String email = uniqueEmail();
    register(email);

    String wrongPassword =
        postJson("/api/auth/login", Map.of("email", email, "password", "Wrong1234"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknownEmail =
        postJson("/api/auth/login", Map.of("email", uniqueEmail(), "password", "Wrong1234"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(wrongPassword).isEqualTo(unknownEmail);
  }

  @Test
  @DisplayName("login accepts any casing of the email")
  void login_uppercaseEmail_succeeds() throws Exception {
    String email = uniqueEmail();
    register(email);

    postJson("/api/auth/login", Map.of("email", email.toUpperCase(), "password", PASSWORD))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("a locked account cannot log in or refresh")
  void lockedAccount_cannotLoginOrRefresh() throws Exception {
    String email = uniqueEmail();
    JsonNode session = register(email);
    setStatus(email, "SUSPENDED");

    postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD))
        .andExpect(status().isForbidden());
    refresh(session.get("refreshToken").asText()).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("me requires a valid token")
  void me_requiresValidToken() throws Exception {
    JsonNode session = register(uniqueEmail());
    String accessToken = session.get("accessToken").asText();

    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(jsonPath("$.message").isNotEmpty());
    getWithToken("/api/users/me", accessToken + "x").andExpect(status().isUnauthorized());
    getWithToken("/api/users/me", accessToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(session.at("/user/email").asText()));
  }

  @Test
  @DisplayName("refresh rotates the token, also when a stale access token is attached")
  void refresh_rotatesToken() throws Exception {
    JsonNode session = register(uniqueEmail());

    JsonNode rotated =
        body(
            mockMvc
                .perform(
                    post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.or.invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json(Map.of("refreshToken", session.get("refreshToken").asText()))))
                .andExpect(status().isOk()));

    assertThat(rotated.get("refreshToken").asText())
        .isNotEqualTo(session.get("refreshToken").asText());
    getWithToken("/api/users/me", rotated.get("accessToken").asText()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("replaying a used refresh token revokes the whole session")
  void refresh_replayedToken_revokesFamily() throws Exception {
    JsonNode session = register(uniqueEmail());
    String first = session.get("refreshToken").asText();
    String second = body(refresh(first).andExpect(status().isOk())).get("refreshToken").asText();

    refresh(first).andExpect(status().isUnauthorized());

    refresh(second).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("refresh rejects an unknown token")
  void refresh_unknownToken_returnsUnauthorized() throws Exception {
    refresh("not-a-real-token").andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("refresh tokens are stored only as hashes")
  void refreshTokens_areStoredHashed() throws Exception {
    String refreshToken = register(uniqueEmail()).get("refreshToken").asText();

    assertThat(refreshTokenRepository.findAll())
        .allSatisfy(
            token -> assertThat(token.getTokenHash()).hasSize(64).isNotEqualTo(refreshToken));
  }

  @Test
  @DisplayName("revoke ends only that session and needs no access token")
  void revoke_endsThatSessionWithoutAccessToken() throws Exception {
    String email = uniqueEmail();
    JsonNode first = register(email);
    JsonNode second =
        body(postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD)));

    postJson("/api/auth/revoke", Map.of("refreshToken", first.get("refreshToken").asText()))
        .andExpect(status().isNoContent());

    refresh(first.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    refresh(second.get("refreshToken").asText()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("revoke does not reveal whether a token exists")
  void revoke_unknownToken_returnsNoContent() throws Exception {
    postJson("/api/auth/revoke", Map.of("refreshToken", "not-a-real-token"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("logout ends every session of the user")
  void logout_endsAllSessions() throws Exception {
    String email = uniqueEmail();
    JsonNode first = register(email);
    JsonNode second =
        body(postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD)));

    mockMvc
        .perform(
            post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, bearer(first.get("accessToken").asText())))
        .andExpect(status().isNoContent());

    refresh(first.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    refresh(second.get("refreshToken").asText()).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("admin login matches the Admin Web contract and may list users")
  void admin_canListUsers() throws Exception {
    JsonNode session =
        body(
            postJson("/api/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.admin.role").value("ADMIN")));

    getWithToken("/api/admin/users?page=0&size=5", session.get("accessToken").asText())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.size").value(5));
  }

  @Test
  @DisplayName("admin list rejects an oversized page")
  void admin_oversizedPage_returnsBadRequest() throws Exception {
    JsonNode session =
        body(postJson("/api/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)));

    getWithToken("/api/admin/users?size=101", session.get("accessToken").asText())
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("a traveller gets 403 on admin routes and no token gets 401")
  void adminRoutes_enforceRole() throws Exception {
    getWithToken("/api/admin/users", register(uniqueEmail()).get("accessToken").asText())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").isNotEmpty());
    mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("health is public, other routes are denied by default")
  void routes_denyByDefault() throws Exception {
    mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    mockMvc.perform(get("/api/anything")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("malformed JSON returns the standard error shape")
  void malformedJson_returnsBadRequest() throws Exception {
    mockMvc
        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{oops"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").isNotEmpty());
  }

  @Test
  @DisplayName("an unknown route returns 404 with the standard error shape when authenticated")
  void unknownRoute_returnsNotFound() throws Exception {
    getWithToken("/api/anything", register(uniqueEmail()).get("accessToken").asText())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").isNotEmpty());
  }

  private void setStatus(String email, String status) {
    User user = userRepository.findByEmail(email).orElseThrow();
    user.setStatus(status);
    userRepository.save(user);
  }

  private JsonNode register(String email) throws Exception {
    return body(
        postJson("/api/auth/register", Map.of("name", "Nam", "email", email, "password", PASSWORD))
            .andExpect(status().isCreated()));
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return postJson("/api/auth/refresh", Map.of("refreshToken", refreshToken));
  }

  private ResultActions getWithToken(String path, String accessToken) throws Exception {
    return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
  }

  private ResultActions postJson(String path, Map<String, String> payload) throws Exception {
    return mockMvc.perform(
        post(path).contentType(MediaType.APPLICATION_JSON).content(json(payload)));
  }

  private JsonNode body(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private String json(Map<String, String> payload) throws Exception {
    return objectMapper.writeValueAsString(payload);
  }

  private static String bearer(String accessToken) {
    return "Bearer " + accessToken;
  }

  private static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@dtrps.test";
  }
}
