package com.dtrps.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.dtrps.modules.auth.entity.EmailOtp;
import com.dtrps.modules.auth.service.OtpMailer;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AccountVerificationApiTest extends ApiTestBase {
  private static final String NEW_PASSWORD = "Moi12345";

  @MockitoBean private OtpMailer mailer;

  /** The code the (mocked) mailer was asked to send to {@code email}. */
  private String sentCode(String email, String purpose) {
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(mailer).send(eq(email), any(), code.capture(), eq(purpose));
    return code.getValue();
  }

  private static String wrong(String code) {
    return code.equals("000000") ? "111111" : "000000";
  }

  @Test
  @DisplayName("sign-up: name + email → emailed code → password; the account can then sign in")
  void register_withEmailCode() throws Exception {
    String email = uniqueEmail();
    postAs("/api/auth/register/otp", null, Map.of("name", "Lan Anh", "email", email))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresInSeconds").value(600))
        .andExpect(jsonPath("$.resendInSeconds").value(60));
    String code = sentCode(email, EmailOtp.REGISTER);

    // Gửi lại ngay → phải chờ
    postAs("/api/auth/register/otp", null, Map.of("name", "Lan Anh", "email", email))
        .andExpect(status().isTooManyRequests());

    postAs("/api/auth/register/verify", null, Map.of("email", email, "code", wrong(code)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Mã xác thực không đúng, bạn còn 4 lần thử"));
    String ticket =
        json(postAs("/api/auth/register/verify", null, Map.of("email", email, "code", code)))
            .get("ticket")
            .asText();

    Map<String, String> finish = Map.of("ticket", ticket, "name", "Lan Anh", "password", PASSWORD);
    postAs("/api/auth/register/complete", null, finish)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.user.email").value(email));
    assertThat(login(email, PASSWORD)).isNotBlank();

    // Vé chỉ dùng một lần
    postAs("/api/auth/register/complete", null, finish).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("sign-up code: an existing email is refused; five wrong tries need a new code")
  void register_rules() throws Exception {
    String taken = uniqueEmail();
    session(taken);
    postAs("/api/auth/register/otp", null, Map.of("name", "Nam", "email", taken))
        .andExpect(status().isConflict());

    String email = uniqueEmail();
    postAs("/api/auth/register/otp", null, Map.of("name", "Nam", "email", email))
        .andExpect(status().isOk());
    String code = sentCode(email, EmailOtp.REGISTER);
    for (int i = 0; i < 5; i++) {
      postAs("/api/auth/register/verify", null, Map.of("email", email, "code", wrong(code)));
    }
    postAs("/api/auth/register/verify", null, Map.of("email", email, "code", code))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message").value("Bạn đã nhập sai quá nhiều lần, vui lòng gửi lại mã mới"));
  }

  @Test
  @DisplayName("password reset: code → new password; old password stops working")
  void resetPassword_withEmailCode() throws Exception {
    String email = uniqueEmail();
    session(email);
    postAs("/api/auth/password/otp", null, Map.of("email", email)).andExpect(status().isOk());
    String code = sentCode(email, EmailOtp.RESET_PASSWORD);
    String ticket =
        json(postAs("/api/auth/password/verify", null, Map.of("email", email, "code", code)))
            .get("ticket")
            .asText();

    postAs("/api/auth/password/reset", null, Map.of("ticket", ticket, "newPassword", "yeu"))
        .andExpect(status().isBadRequest());
    postAs("/api/auth/password/reset", null, Map.of("ticket", ticket, "newPassword", NEW_PASSWORD))
        .andExpect(status().isNoContent());

    assertThat(login(email, NEW_PASSWORD)).isNotBlank();
    postAs("/api/auth/login", null, Map.of("email", email, "password", PASSWORD))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("password reset for an unknown email answers the same but sends nothing")
  void resetPassword_unknownEmail() throws Exception {
    String email = uniqueEmail();
    postAs("/api/auth/password/otp", null, Map.of("email", email))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresInSeconds").value(600));
    verify(mailer, never()).send(eq(email), any(), anyString(), any());
  }
}
