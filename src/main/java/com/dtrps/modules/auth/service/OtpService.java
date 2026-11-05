package com.dtrps.modules.auth.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.auth.config.OtpProperties;
import com.dtrps.modules.auth.dto.OtpSentResponse;
import com.dtrps.modules.auth.entity.EmailOtp;
import com.dtrps.modules.auth.repository.EmailOtpRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email verification codes: send (with resend wait and an hourly cap), check (limited tries), then
 * a one-time ticket that finishes sign-up or the password reset. Only hashes are stored.
 */
@Service
public class OtpService {
  private static final int CODE_DIGITS = 6;
  private static final int MAX_PER_HOUR = 6;
  private static final String EXPIRED = "Mã xác thực đã hết hạn, vui lòng gửi lại mã mới";
  private static final String TOO_MANY_TRIES =
      "Bạn đã nhập sai quá nhiều lần, vui lòng gửi lại mã mới";
  private static final String TOO_MANY_SENDS =
      "Bạn đã yêu cầu quá nhiều mã, vui lòng thử lại sau một lúc";
  private static final String TICKET_INVALID =
      "Phiên xác thực đã hết hạn, vui lòng làm lại từ bước nhập email";

  private final EmailOtpRepository otpRepository;
  private final OtpMailer mailer;
  private final OtpProperties properties;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public OtpService(
      EmailOtpRepository otpRepository, OtpMailer mailer, OtpProperties properties, Clock clock) {
    this.otpRepository = otpRepository;
    this.mailer = mailer;
    this.properties = properties;
    this.clock = clock;
  }

  /** Emails a new code. {@code deliver} false: same answer and timing, but nothing is sent. */
  @Transactional
  public OtpSentResponse send(String email, String name, String purpose, boolean deliver) {
    Instant now = clock.instant();
    otpRepository
        .findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByOtpIdDesc(email, purpose)
        .ifPresent(
            latest -> {
              long wait =
                  Duration.between(now, latest.getCreatedAt().plus(properties.resendAfter()))
                      .toSeconds();
              if (wait > 0) {
                throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Vui lòng chờ " + wait + " giây trước khi gửi lại mã");
              }
            });
    if (otpRepository.countByEmailAndPurposeAndCreatedAtAfter(
            email, purpose, now.minus(Duration.ofHours(1)))
        >= MAX_PER_HOUR) {
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_SENDS);
    }

    String code = newCode();
    otpRepository.retire(email, purpose, now);
    EmailOtp otp = new EmailOtp();
    otp.setEmail(email);
    otp.setPurpose(purpose);
    otp.setCodeHash(hash(purpose + ":" + email + ":" + code));
    otp.setExpiresAt(now.plus(properties.ttl()));
    otp.setCreatedAt(now);
    otpRepository.save(otp);
    if (deliver) {
      mailer.send(email, name, code, purpose);
    }
    return new OtpSentResponse(
        email, properties.ttl().toSeconds(), properties.resendAfter().toSeconds());
  }

  /** Checks the code; a match returns a one-time ticket. Wrong tries are kept on rejection. */
  @Transactional(noRollbackFor = ApiException.class)
  public String verify(String email, String code, String purpose) {
    Instant now = clock.instant();
    EmailOtp otp =
        otpRepository
            .findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByOtpIdDesc(email, purpose)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, EXPIRED));
    if (!now.isBefore(otp.getExpiresAt())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, EXPIRED);
    }
    if (otp.getAttempts() >= properties.maxAttempts()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, TOO_MANY_TRIES);
    }
    boolean matches =
        MessageDigest.isEqual(
            otp.getCodeHash().getBytes(StandardCharsets.UTF_8),
            hash(purpose + ":" + email + ":" + code.trim()).getBytes(StandardCharsets.UTF_8));
    if (!matches) {
      otp.setAttempts(otp.getAttempts() + 1);
      otpRepository.saveAndFlush(otp);
      int left = properties.maxAttempts() - otp.getAttempts();
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          left > 0 ? "Mã xác thực không đúng, bạn còn " + left + " lần thử" : TOO_MANY_TRIES);
    }
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    otp.setVerifiedAt(now);
    otp.setTicketHash(hash(ticket));
    otp.setTicketExpiresAt(now.plus(properties.ticketTtl()));
    return ticket;
  }

  /** Uses up a ticket from {@link #verify}; returns the verified email. */
  @Transactional
  public String consume(String ticket, String purpose) {
    Instant now = clock.instant();
    EmailOtp otp =
        otpRepository
            .findFirstByTicketHashAndPurpose(hash(ticket.trim()), purpose)
            .filter(found -> found.getConsumedAt() == null)
            .filter(found -> now.isBefore(found.getTicketExpiresAt()))
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, TICKET_INVALID));
    otp.setConsumedAt(now);
    return otp.getEmail();
  }

  public long ticketSeconds() {
    return properties.ticketTtl().toSeconds();
  }

  private String newCode() {
    int bound = (int) Math.pow(10, CODE_DIGITS);
    return String.format("%0" + CODE_DIGITS + "d", random.nextInt(bound));
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
