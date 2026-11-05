package com.dtrps.modules.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class UserApiTest extends ApiTestBase {
  private static final String NEW_PASSWORD = "NewPassword456";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("a user updates and reads back their profile")
  void updateProfile_savesFields() throws Exception {
    String token = travellerToken();

    putAs(
            "/api/users/me",
            token,
            Map.of(
                "name", "Trần An",
                "phoneNumber", "+84901234567",
                "dateOfBirth", "1995-04-30",
                "avatarUrl", "https://cdn.example.com/a.png"))
        .andExpect(status().isOk());

    getAs("/api/users/me", token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Trần An"))
        .andExpect(jsonPath("$.phoneNumber").value("+84901234567"))
        .andExpect(jsonPath("$.dateOfBirth").value("1995-04-30"));
  }

  @Test
  @DisplayName("profile update rejects bad input and reports the fields")
  void updateProfile_invalidInput_returnsFieldErrors() throws Exception {
    putAs(
            "/api/users/me",
            travellerToken(),
            Map.of(
                "name", "A",
                "phoneNumber", "abc",
                "dateOfBirth", "2999-01-01",
                "avatarUrl", "javascript:alert(1)"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.name").isNotEmpty())
        .andExpect(jsonPath("$.errors.phoneNumber").isNotEmpty())
        .andExpect(jsonPath("$.errors.dateOfBirth").isNotEmpty())
        .andExpect(jsonPath("$.errors.avatarUrl").isNotEmpty());
  }

  @Test
  @DisplayName("profile routes need a token")
  void profile_withoutToken_returnsUnauthorized() throws Exception {
    getAs("/api/users/me", null).andExpect(status().isUnauthorized());
    getAs("/api/users/me/preferences", null).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("preferences start empty, then save several options of each kind")
  void preferences_roundTrip() throws Exception {
    String token = travellerToken();
    getAs("/api/users/me/preferences", token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.interests").isEmpty());

    putAs(
            "/api/users/me/preferences",
            token,
            Map.of(
                "interests", List.of("restaurant", "scenicSpot"),
                "specialNeeds", List.of("kids")))
        .andExpect(status().isOk());

    putAs("/api/users/me/preferences", token, Map.of("interests", List.of("heritage")))
        .andExpect(status().isOk());

    getAs("/api/users/me/preferences", token)
        .andExpect(jsonPath("$.interests.length()").value(1))
        .andExpect(jsonPath("$.interests[0]").value("heritage"));
  }

  @Test
  @DisplayName("preferences reject an unknown code or value")
  void preferences_invalidValues_returnBadRequest() throws Exception {
    String token = travellerToken();

    putAs("/api/users/me/preferences", token, Map.of("interests", List.of("not-a-code")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("change password replaces the password and ends every session")
  void changePassword_success_endsSessions() throws Exception {
    String email = uniqueEmail();
    JsonNode session = session(email);

    postAs(
            "/api/auth/password/change",
            session.get("accessToken").asText(),
            Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
        .andExpect(status().isNoContent());

    postAs("/api/auth/login", null, Map.of("email", email, "password", PASSWORD))
        .andExpect(status().isUnauthorized());
    postAs("/api/auth/login", null, Map.of("email", email, "password", NEW_PASSWORD))
        .andExpect(status().isOk());
    postAs("/api/auth/refresh", null, Map.of("refreshToken", session.get("refreshToken").asText()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("change password rejects a wrong current password and a weak new one")
  void changePassword_invalid_returnsBadRequest() throws Exception {
    String token = travellerToken();

    postAs(
            "/api/auth/password/change",
            token,
            Map.of("currentPassword", "Wrong12345", "newPassword", NEW_PASSWORD))
        .andExpect(status().isBadRequest());
    postAs(
            "/api/auth/password/change",
            token,
            Map.of("currentPassword", PASSWORD, "newPassword", "weak"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.newPassword").isNotEmpty());
  }

  @Test
  @DisplayName("an admin reads a user, and suspending cuts off login and refresh")
  void admin_suspendsUser() throws Exception {
    String email = uniqueEmail();
    JsonNode session = session(email);
    long userId = session.at("/user/id").asLong();
    String admin = adminToken();

    getAs("/api/admin/users/" + userId, admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email));
    putAs("/api/admin/users/" + userId + "/status", admin, Map.of("status", "SUSPENDED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    postAs("/api/auth/login", null, Map.of("email", email, "password", PASSWORD))
        .andExpect(status().isForbidden());
    postAs("/api/auth/refresh", null, Map.of("refreshToken", session.get("refreshToken").asText()))
        .andExpect(status().isUnauthorized());

    putAs("/api/admin/users/" + userId + "/status", admin, Map.of("status", "ACTIVE"))
        .andExpect(status().isOk());
    postAs("/api/auth/login", null, Map.of("email", email, "password", PASSWORD))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("status changes and password changes leave an audit row")
  void sensitiveActions_areAudited() throws Exception {
    JsonNode session = session(uniqueEmail());
    long userId = session.at("/user/id").asLong();

    putAs("/api/admin/users/" + userId + "/status", adminToken(), Map.of("status", "SUSPENDED"))
        .andExpect(status().isOk());
    putAs("/api/admin/users/" + userId + "/status", adminToken(), Map.of("status", "ACTIVE"))
        .andExpect(status().isOk());
    postAs(
            "/api/auth/password/change",
            session.get("accessToken").asText(),
            Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
        .andExpect(status().isNoContent());

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT action FROM audit_logs WHERE entity_id = ? ORDER BY log_id",
                String.class,
                userId))
        .containsExactly("USER_STATUS_SUSPENDED", "USER_STATUS_ACTIVE", "PASSWORD_CHANGED");
  }

  @Test
  @DisplayName("an admin cannot suspend their own account or set an unknown status")
  void admin_statusGuards() throws Exception {
    JsonNode adminSession =
        json(
            postAs(
                "/api/auth/login", null, Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)));
    String admin = adminSession.get("accessToken").asText();
    long adminId = adminSession.at("/user/id").asLong();

    putAs("/api/admin/users/" + adminId + "/status", admin, Map.of("status", "SUSPENDED"))
        .andExpect(status().isBadRequest());
    putAs("/api/admin/users/" + adminId + "/status", admin, Map.of("status", "DELETED"))
        .andExpect(status().isBadRequest());
    assertThat(adminId).isPositive();
  }

  @Test
  @DisplayName("a traveller cannot use admin user routes, and a missing user is 404")
  void adminRoutes_enforceAccess() throws Exception {
    String traveller = travellerToken();

    getAs("/api/admin/users/1", traveller).andExpect(status().isForbidden());
    putAs("/api/admin/users/1/status", traveller, Map.of("status", "SUSPENDED"))
        .andExpect(status().isForbidden());
    getAs("/api/admin/users/999999999", adminToken()).andExpect(status().isNotFound());
  }
}
