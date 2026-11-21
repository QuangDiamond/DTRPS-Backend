package com.dtrps.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.auth.dto.CompleteRegistrationRequest;
import com.dtrps.modules.auth.dto.EmailRequest;
import com.dtrps.modules.auth.dto.RegisterOtpRequest;
import com.dtrps.modules.auth.entity.EmailOtp;
import com.dtrps.modules.user.entity.RoleName;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Unit test AccountVerificationService — sheet Auth, UTC001–UTC004 và UTC014–UTC019 trong
 * Report5.1.
 */
@ExtendWith(MockitoExtension.class)
class AccountVerificationServiceTest {
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();
  private static final String TICKET_EXPIRED =
      "Phiên xác thực đã hết hạn, vui lòng làm lại từ bước nhập email";

  @Mock private OtpService otpService;
  @Mock private AuthService authService;
  @Mock private UserRepository users;

  private AccountVerificationService service;

  @BeforeEach
  void setUp() {
    service = new AccountVerificationService(otpService, authService, users);
  }

  private static Set<String> messages(Object request) {
    return VALIDATOR.validate(request).stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  @Nested
  @DisplayName("sendRegisterCode")
  class SendRegisterCode {
    @Test
    @DisplayName("UTC001 Email without account → code sent to the normalized email")
    void newEmail_sendsCode() {
      RegisterOtpRequest request = new RegisterOtpRequest(" Nguyễn Văn A ", "A@Gmail.com");
      assertThat(messages(request)).isEmpty();

      service.sendRegisterCode(request);

      verify(otpService).send("a@gmail.com", "Nguyễn Văn A", EmailOtp.REGISTER, true);
    }

    @Test
    @DisplayName("UTC002 Email already has an account (different case) → 409, no code")
    void takenEmail_conflict() {
      when(users.existsByEmail("a@gmail.com")).thenReturn(true);

      ApiException e =
          error(
              () ->
                  service.sendRegisterCode(new RegisterOtpRequest("Nguyễn Văn A", "A@Gmail.com")));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Email này đã có tài khoản — hãy đăng nhập");
      verify(otpService, never()).send(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("UTC003 email=\"abc\" → 400 (validation)")
    void badEmail_invalid() {
      assertThat(messages(new RegisterOtpRequest("Nguyễn Văn A", "abc")))
          .containsExactly("Email không hợp lệ");
    }

    @Test
    @DisplayName("UTC004 Name of 1 character → 400 (validation); 2 characters accepted")
    void shortName_invalid() {
      assertThat(messages(new RegisterOtpRequest("A", "a@gmail.com")))
          .containsExactly("Họ tên phải có từ 2 đến 150 ký tự");
      assertThat(messages(new RegisterOtpRequest("An", "a@gmail.com"))).isEmpty();
    }
  }

  @Nested
  @DisplayName("completeRegistration")
  class CompleteRegistration {
    @Test
    @DisplayName("UTC014 Verified ticket → traveller account created with the verified email")
    void validTicket_creates() {
      when(otpService.consume("ticket-1", EmailOtp.REGISTER)).thenReturn("a@gmail.com");
      CompleteRegistrationRequest request =
          new CompleteRegistrationRequest("ticket-1", " Nguyễn Văn A ", "Abc@1234");
      assertThat(messages(request)).isEmpty();

      service.completeRegistration(request);

      verify(authService).registerVerified("Nguyễn Văn A", "a@gmail.com", "Abc@1234");
    }

    @Test
    @DisplayName("UTC015 Empty password → 400 (validation)")
    void emptyPassword_invalid() {
      assertThat(messages(new CompleteRegistrationRequest("ticket-1", "Nguyễn Văn A", "")))
          .contains("Vui lòng nhập mật khẩu");
    }

    @Test
    @DisplayName("UTC016 Ticket older than 15 minutes → 400, no account created")
    void expiredTicket_badRequest() {
      when(otpService.consume("old", EmailOtp.REGISTER))
          .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, TICKET_EXPIRED));

      ApiException e =
          error(
              () ->
                  service.completeRegistration(
                      new CompleteRegistrationRequest("old", "Nguyễn Văn A", "Abc@1234")));

      assertThat(e.getMessage()).isEqualTo(TICKET_EXPIRED);
      verify(authService, never()).registerVerified(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("UTC038 accountType=BUSINESS_OWNER → partner account created with BUSINESS_OWNER")
    void partnerAccountType_createsPartner() {
      when(otpService.consume("ticket-2", EmailOtp.REGISTER)).thenReturn("partner@gmail.com");
      CompleteRegistrationRequest request =
          new CompleteRegistrationRequest(
              "ticket-2", " Chủ Quán ", "Abc@1234", "BUSINESS_OWNER");
      assertThat(messages(request)).isEmpty();

      service.completeRegistration(request);

      verify(authService)
          .registerVerified("Chủ Quán", "partner@gmail.com", "Abc@1234", RoleName.BUSINESS_OWNER);
    }

    @Test
    @DisplayName("UTC039 accountType=\"ADMIN\" → 400 (validation)")
    void invalidAccountType_invalid() {
      CompleteRegistrationRequest request =
          new CompleteRegistrationRequest("ticket-2", "Chủ Quán", "Abc@1234", "ADMIN");
      assertThat(messages(request))
          .containsExactly("accountType phải là TRAVELLER hoặc BUSINESS_OWNER");
    }
  }

  @Nested
  @DisplayName("sendResetCode")
  class SendResetCode {
    @Test
    @DisplayName("UTC017 Email of an active account → code delivered")
    void activeAccount_delivers() {
      User user = new User();
      user.setFullName("Nguyễn Văn A");
      when(users.findByEmail("a@gmail.com")).thenReturn(Optional.of(user));

      service.sendResetCode(new EmailRequest("a@gmail.com"));

      verify(otpService).send("a@gmail.com", "Nguyễn Văn A", EmailOtp.RESET_PASSWORD, true);
    }

    @Test
    @DisplayName("UTC018 Unknown email → same answer, nothing delivered")
    void unknownEmail_notDelivered() {
      service.sendResetCode(new EmailRequest("x@gmail.com"));

      verify(otpService).send("x@gmail.com", null, EmailOtp.RESET_PASSWORD, false);
    }

    @Test
    @DisplayName("UTC019 Empty email → 400 (validation)")
    void emptyEmail_invalid() {
      assertThat(messages(new EmailRequest(""))).containsExactly("Vui lòng nhập email");
    }

    @Test
    @DisplayName("Locked account → same answer, nothing delivered")
    void lockedAccount_notDelivered() {
      User user = new User();
      user.setStatus("SUSPENDED");
      when(users.findByEmail("a@gmail.com")).thenReturn(Optional.of(user));

      service.sendResetCode(new EmailRequest("a@gmail.com"));

      verify(otpService).send("a@gmail.com", null, EmailOtp.RESET_PASSWORD, false);
    }
  }
}
