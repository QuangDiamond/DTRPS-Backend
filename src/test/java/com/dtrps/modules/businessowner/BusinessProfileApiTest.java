package com.dtrps.modules.businessowner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessProfileApiTest extends ApiTestBase {
  private static final String OWN = "/api/business-profile";
  private static final String ADMIN = "/api/admin/business-profiles";

  private static String uniqueTaxCode() {
    return String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
  }

  private static Map<String, String> profileBody(String taxCode) {
    Map<String, String> body = new HashMap<>();
    body.put("businessName", "Hội An Garden Homestay");
    body.put("taxCode", taxCode);
    body.put("contactEmail", "lienhe@hoiangarden.vn");
    body.put("contactPhone", "0905128991");
    return body;
  }

  /** Registers a profile for a new user and returns {email, token, id}. */
  private Map<String, String> registeredOwner() throws Exception {
    String email = uniqueEmail();
    String token = session(email).get("accessToken").asText();
    JsonNode profile = json(postAs(OWN, token, profileBody(uniqueTaxCode())));
    return Map.of("email", email, "token", token, "id", profile.get("id").asText());
  }

  @Test
  @DisplayName("a user registers a business profile, which starts PENDING")
  void register_createsPendingProfile() throws Exception {
    String token = travellerToken();
    String taxCode = uniqueTaxCode();

    postAs(OWN, token, profileBody(taxCode))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.taxCode").value(taxCode))
        .andExpect(jsonPath("$.ownerName").value("Nam"));

    getAs(OWN, token).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  @DisplayName("a user has at most one profile and a tax code belongs to one business")
  void register_rejectsDuplicates() throws Exception {
    String token = travellerToken();
    String taxCode = uniqueTaxCode();
    postAs(OWN, token, profileBody(taxCode)).andExpect(status().isCreated());

    postAs(OWN, token, profileBody(uniqueTaxCode())).andExpect(status().isConflict());
    postAs(OWN, travellerToken(), profileBody(taxCode)).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("registration validates the fields")
  void register_invalidInput_returnsFieldErrors() throws Exception {
    Map<String, String> body = profileBody("12345");
    body.put("businessName", "");
    body.put("contactEmail", "not-an-email");
    body.put("contactPhone", "abc");

    postAs(OWN, travellerToken(), body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.businessName").isNotEmpty())
        .andExpect(jsonPath("$.errors.taxCode").isNotEmpty())
        .andExpect(jsonPath("$.errors.contactEmail").isNotEmpty())
        .andExpect(jsonPath("$.errors.contactPhone").isNotEmpty());
  }

  @Test
  @DisplayName("a user without a profile gets 404")
  void getOwn_withoutProfile_returnsNotFound() throws Exception {
    getAs(OWN, travellerToken()).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("approval activates the profile and grants BUSINESS_OWNER on the next login")
  void approve_activatesAndGrantsRole() throws Exception {
    Map<String, String> owner = registeredOwner();

    postAs(ADMIN + "/" + owner.get("id") + "/approve", adminToken(), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.approvedAt").isNotEmpty());

    JsonNode relogin =
        json(
            postAs(
                "/api/auth/login",
                null,
                Map.of("email", owner.get("email"), "password", PASSWORD)));
    assertThat(relogin.get("user").get("roles").toString()).contains("BUSINESS_OWNER");

    postAs(ADMIN + "/" + owner.get("id") + "/approve", adminToken(), null)
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("a rejected profile shows the reason, and editing it resubmits for review")
  void reject_thenResubmit() throws Exception {
    Map<String, String> owner = registeredOwner();

    postAs(
            ADMIN + "/" + owner.get("id") + "/reject",
            adminToken(),
            Map.of("reason", "Mã số thuế không khớp tên doanh nghiệp"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));

    getAs(OWN, owner.get("token"))
        .andExpect(jsonPath("$.status").value("INACTIVE"))
        .andExpect(jsonPath("$.statusNote").value("Mã số thuế không khớp tên doanh nghiệp"));

    putAs(OWN, owner.get("token"), profileBody(uniqueTaxCode()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.statusNote").isEmpty());
  }

  @Test
  @DisplayName("rejecting needs a reason")
  void reject_withoutReason_returnsBadRequest() throws Exception {
    Map<String, String> owner = registeredOwner();
    postAs(ADMIN + "/" + owner.get("id") + "/reject", adminToken(), Map.of("reason", " "))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("an admin locks an active business with a reason and unlocks it")
  void lockAndUnlock() throws Exception {
    Map<String, String> owner = registeredOwner();
    String admin = adminToken();
    String path = ADMIN + "/" + owner.get("id");

    putAs(path + "/status", admin, Map.of("status", "SUSPENDED", "reason", "x"))
        .andExpect(status().isConflict());

    postAs(path + "/approve", admin, null).andExpect(status().isOk());

    putAs(path + "/status", admin, Map.of("status", "SUSPENDED"))
        .andExpect(status().isBadRequest());
    putAs(path + "/status", admin, Map.of("status", "SUSPENDED", "reason", "Giấy phép hết hạn"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"))
        .andExpect(jsonPath("$.statusNote").value("Giấy phép hết hạn"));

    putAs(OWN, owner.get("token"), profileBody(uniqueTaxCode())).andExpect(status().isConflict());

    putAs(path + "/status", admin, Map.of("status", "ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  @DisplayName("admins filter the list by status and keyword")
  void list_filtersByStatusAndKeyword() throws Exception {
    Map<String, String> owner = registeredOwner();
    String admin = adminToken();

    getAs(ADMIN + "?status=PENDING&keyword=" + owner.get("email"), admin)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(Long.valueOf(owner.get("id"))));

    getAs(ADMIN + "?status=ACTIVE&keyword=" + owner.get("email"), admin)
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  @DisplayName("stats count profiles by status, including this month's registrations")
  void stats_countByStatus() throws Exception {
    String admin = adminToken();
    JsonNode before = json(getAs(ADMIN + "/stats", admin));

    Map<String, String> pending = registeredOwner();
    Map<String, String> approved = registeredOwner();
    postAs(ADMIN + "/" + approved.get("id") + "/approve", admin, null).andExpect(status().isOk());

    JsonNode after = json(getAs(ADMIN + "/stats", admin).andExpect(status().isOk()));
    assertThat(after.get("total").asLong()).isEqualTo(before.get("total").asLong() + 2);
    assertThat(after.get("pending").asLong()).isEqualTo(before.get("pending").asLong() + 1);
    assertThat(after.get("active").asLong()).isEqualTo(before.get("active").asLong() + 1);
    assertThat(after.get("newThisMonth").asLong())
        .isEqualTo(before.get("newThisMonth").asLong() + 2);
    assertThat(pending.get("id")).isNotBlank();
  }

  @Test
  @DisplayName("admin routes are for admins only")
  void adminRoutes_forbiddenForTravellers() throws Exception {
    getAs(ADMIN, travellerToken()).andExpect(status().isForbidden());
    getAs(ADMIN, null).andExpect(status().isUnauthorized());
  }
}
