package com.dtrps.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.auth.config.OtpProperties;
import com.dtrps.modules.auth.dto.OtpSentResponse;
import com.dtrps.modules.auth.entity.EmailOtp;
import com.dtrps.modules.auth.repository.EmailOtpRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Unit test OtpService — sheet Auth, UTC005–UTC013 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class OtpServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final String EMAIL = "a@gmail.com";
  private static final String REGISTER = EmailOtp.REGISTER;
  private static final OtpProperties PROPERTIES =
      new OtpProperties(
          Duration.ofMinutes(10), Duration.ofSeconds(60), 5, Duration.ofMinutes(15), "SmartTravel");

  @Mock private EmailOtpRepository otps;
  @Mock private OtpMailer mailer;

  private OtpService service;

  @BeforeEach
  void setUp() {
    service = new OtpService(otps, mailer, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** Mã đang chờ cho {@code code}, tạo lúc {@code createdAt} */
  private EmailOtp pending(String code, Instant createdAt) {
    EmailOtp otp = new EmailOtp();
    otp.setEmail(EMAIL);
    otp.setPurpose(REGISTER);
    otp.setCodeHash(OtpService.hash(REGISTER + ":" + EMAIL + ":" + code));
    otp.setCreatedAt(createdAt);
    otp.setExpiresAt(createdAt.plus(PROPERTIES.ttl()));
    lenient()
        .when(otps.findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByOtpIdDesc(EMAIL, REGISTER))
        .thenReturn(Optional.of(otp));
    return otp;
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  @Nested
  @DisplayName("send")
  class Send {
    @Test
    @DisplayName("UTC005 No code yet → 6-digit code emailed, only its hash stored")
    void first_sendsCode() {
      OtpSentResponse response = service.send(EMAIL, "Nguyễn Văn A", REGISTER, true);

      ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
      verify(mailer).send(eq(EMAIL), eq("Nguyễn Văn A"), code.capture(), eq(REGISTER));
      assertThat(code.getValue()).matches("\\d{6}");
      ArgumentCaptor<EmailOtp> saved = ArgumentCaptor.forClass(EmailOtp.class);
      verify(otps).save(saved.capture());
      assertThat(saved.getValue().getCodeHash())
          .isEqualTo(OtpService.hash(REGISTER + ":" + EMAIL + ":" + code.getValue()))
          .doesNotContain(code.getValue());
      assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
      assertThat(response.expiresInSeconds()).isEqualTo(600);
      assertThat(response.resendInSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("UTC006 Code sent 30 seconds ago → 429, wait 30 more seconds")
    void tooSoon_tooManyRequests() {
      pending("123456", NOW.minusSeconds(30));

      ApiException e = error(() -> service.send(EMAIL, "A", REGISTER, true));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
      assertThat(e.getMessage()).isEqualTo("Vui lòng chờ 30 giây trước khi gửi lại mã");
      verify(mailer, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("UTC007 6 codes sent within the last hour → 7th request 429")
    void hourlyCap_tooManyRequests() {
      when(otps.countByEmailAndPurposeAndCreatedAtAfter(
              EMAIL, REGISTER, NOW.minus(Duration.ofHours(1))))
          .thenReturn(6L);

      ApiException e = error(() -> service.send(EMAIL, "A", REGISTER, true));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
      assertThat(e.getMessage())
          .isEqualTo("Bạn đã yêu cầu quá nhiều mã, vui lòng thử lại sau một lúc");
      verify(otps, never()).save(any());
    }
  }

  @Nested
  @DisplayName("verify")
  class Verify {
    @Test
    @DisplayName("UTC008 Correct code → one-time ticket, only its hash stored")
    void correct_returnsTicket() {
      EmailOtp otp = pending("123456", NOW.minusSeconds(120));

      String ticket = service.verify(EMAIL, " 123456 ", REGISTER);

      assertThat(ticket).isNotBlank();
      assertThat(otp.getVerifiedAt()).isEqualTo(NOW);
      assertThat(otp.getTicketHash()).isEqualTo(OtpService.hash(ticket));
      assertThat(otp.getTicketExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("UTC009 Wrong code → 400 with the remaining tries, attempt counted")
    void wrong_countsAttempt() {
      EmailOtp otp = pending("123456", NOW.minusSeconds(120));

      ApiException e = error(() -> service.verify(EMAIL, "000000", REGISTER));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Mã xác thực không đúng, bạn còn 4 lần thử");
      assertThat(otp.getAttempts()).isEqualTo(1);
      verify(otps).saveAndFlush(otp);
    }

    @Test
    @DisplayName("UTC010 Already 5 wrong tries → 400 even with the correct code")
    void tooManyTries_badRequest() {
      pending("123456", NOW.minusSeconds(120)).setAttempts(5);

      ApiException e = error(() -> service.verify(EMAIL, "123456", REGISTER));

      assertThat(e.getMessage())
          .isEqualTo("Bạn đã nhập sai quá nhiều lần, vui lòng gửi lại mã mới");
    }

    @Test
    @DisplayName("UTC011 Code older than 10 minutes → 400 expired")
    void expired_badRequest() {
      pending("123456", NOW.minus(Duration.ofMinutes(10)));

      ApiException e = error(() -> service.verify(EMAIL, "123456", REGISTER));

      assertThat(e.getMessage()).isEqualTo("Mã xác thực đã hết hạn, vui lòng gửi lại mã mới");
    }

    @Test
    @DisplayName("5th wrong try → 400 no tries left")
    void lastWrongTry_noTriesLeft() {
      pending("123456", NOW.minusSeconds(120)).setAttempts(4);

      ApiException e = error(() -> service.verify(EMAIL, "000000", REGISTER));

      assertThat(e.getMessage())
          .isEqualTo("Bạn đã nhập sai quá nhiều lần, vui lòng gửi lại mã mới");
    }
  }

  @Nested
  @DisplayName("consume")
  class Consume {
    private EmailOtp verified(Instant ticketExpiresAt) {
      EmailOtp otp = new EmailOtp();
      otp.setEmail(EMAIL);
      otp.setTicketHash(OtpService.hash("ticket-1"));
      otp.setTicketExpiresAt(ticketExpiresAt);
      when(otps.findFirstByTicketHashAndPurpose(OtpService.hash("ticket-1"), REGISTER))
          .thenReturn(Optional.of(otp));
      return otp;
    }

    @Test
    @DisplayName("UTC012 Valid unused ticket → email returned, ticket used up")
    void valid_returnsEmail() {
      EmailOtp otp = verified(NOW.plus(Duration.ofMinutes(5)));

      assertThat(service.consume(" ticket-1 ", REGISTER)).isEqualTo(EMAIL);
      assertThat(otp.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("UTC013 Ticket already used → 400")
    void used_badRequest() {
      verified(NOW.plus(Duration.ofMinutes(5))).setConsumedAt(NOW.minusSeconds(10));

      ApiException e = error(() -> service.consume("ticket-1", REGISTER));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage())
          .isEqualTo("Phiên xác thực đã hết hạn, vui lòng làm lại từ bước nhập email");
    }

    @Test
    @DisplayName("Ticket older than 15 minutes → 400")
    void expired_badRequest() {
      verified(NOW);

      ApiException e = error(() -> service.consume("ticket-1", REGISTER));

      assertThat(e.getMessage())
          .isEqualTo("Phiên xác thực đã hết hạn, vui lòng làm lại từ bước nhập email");
    }
  }
}
