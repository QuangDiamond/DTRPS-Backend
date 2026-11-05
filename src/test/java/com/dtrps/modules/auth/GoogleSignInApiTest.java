package com.dtrps.modules.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.dtrps.IntegrationTestBase;
import com.dtrps.modules.auth.service.GoogleAccount;
import com.dtrps.modules.auth.service.GoogleTokenVerifier;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(properties = "app.google.client-id=test-client.apps.googleusercontent.com")
class GoogleSignInApiTest extends ApiTestBase {
  private static final String GOOGLE = "/api/auth/google";

  @MockitoBean private GoogleTokenVerifier verifier;

  private void googleSays(String email, boolean verified) {
    when(verifier.verify(anyString()))
        .thenReturn(new GoogleAccount(email, verified, "Minh Google", "https://pic.example/a.png"));
  }

  @Test
  @DisplayName("a new Google email gets a traveller account, then signs in to the same account")
  void newEmail_createsAccount() throws Exception {
    String email = uniqueEmail();
    googleSays(email, true);
    postAs(GOOGLE, null, Map.of("accessToken", "token-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newAccount").value(true))
        .andExpect(jsonPath("$.session.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.session.user.email").value(email))
        .andExpect(jsonPath("$.session.user.name").value("Minh Google"))
        .andExpect(jsonPath("$.session.user.roles[0]").value("TRAVELLER"));

    postAs(GOOGLE, null, Map.of("accessToken", "token-2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newAccount").value(false));

    // Chưa có mật khẩu: đăng nhập bằng mật khẩu bị từ chối
    postAs("/api/auth/login", null, Map.of("email", email, "password", PASSWORD))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("a Google email that already has a password account signs in to it")
  void existingAccount_signsIn() throws Exception {
    String email = uniqueEmail();
    session(email);
    googleSays(email.toUpperCase(), true);
    postAs(GOOGLE, null, Map.of("accessToken", "token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newAccount").value(false))
        .andExpect(jsonPath("$.session.user.email").value(email));
  }

  @Test
  @DisplayName("admin accounts and unverified Google emails are refused")
  void adminAndUnverified_refused() throws Exception {
    googleSays(IntegrationTestBase.ADMIN_EMAIL, true);
    postAs(GOOGLE, null, Map.of("accessToken", "token"))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message").value("Tài khoản quản trị chỉ đăng nhập bằng email và mật khẩu"));

    googleSays(uniqueEmail(), false);
    postAs(GOOGLE, null, Map.of("accessToken", "token")).andExpect(status().isUnauthorized());

    postAs(GOOGLE, null, Map.of("accessToken", "")).andExpect(status().isBadRequest());
  }
}
