package com.dtrps.modules.auth.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.auth.config.OtpProperties;
import com.dtrps.modules.auth.entity.EmailOtp;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.eclipse.angus.mail.smtp.SMTPTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Emails a verification code. Without MAIL_USERNAME (local runs) the code goes to the log so the
 * flow can still be tried.
 */
@Component
public class OtpMailer {
  private static final Logger log = LoggerFactory.getLogger(OtpMailer.class);
  private static final String SEND_FAILED =
      "Không gửi được email lúc này, vui lòng thử lại sau ít phút";

  private final JavaMailSender mailSender;
  private final OtpProperties properties;
  private final String from;

  public OtpMailer(
      JavaMailSender mailSender,
      OtpProperties properties,
      @Value("${spring.mail.username:}") String from) {
    this.mailSender = mailSender;
    this.properties = properties;
    this.from = from;
  }

  public void send(String email, String name, String code, String purpose) {
    boolean register = EmailOtp.REGISTER.equals(purpose);
    String subject =
        (register ? "Mã xác thực đăng ký SmartTravel: " : "Mã đặt lại mật khẩu SmartTravel: ")
            + code;
    if (from == null || from.isBlank()) {
      log.warn("MAIL_USERNAME chưa cấu hình — mã {} cho {}: {}", purpose, email, code);
      return;
    }
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(from, properties.fromName());
      helper.setTo(email);
      helper.setSubject(subject);
      helper.setText(plainText(name, code, register), html(name, code, register));
      String reply = deliver(message);
      log.info(
          "Đã gửi mã {} tới {} lúc {} | Message-ID {} | máy chủ mail: {}",
          purpose,
          mask(email),
          Instant.now(),
          message.getMessageID(),
          reply);
    } catch (MessagingException | MailException | UnsupportedEncodingException e) {
      log.error("Không gửi được mã {} tới {}", purpose, mask(email), e);
      throw new ApiException(HttpStatus.BAD_GATEWAY, SEND_FAILED);
    }
  }

  /**
   * Sends over our own SMTP connection when possible, so the server's reply (e.g. Gmail's "250
   * 2.0.0 OK … gsmtp" with its queue id) can be logged; otherwise falls back to the plain sender.
   */
  private String deliver(MimeMessage message) throws MessagingException {
    if (!(mailSender instanceof JavaMailSenderImpl impl)) {
      mailSender.send(message);
      return "(không có phản hồi chi tiết)";
    }
    message.saveChanges();
    try (Transport transport = impl.getSession().getTransport(impl.getProtocol())) {
      transport.connect(impl.getHost(), impl.getPort(), impl.getUsername(), impl.getPassword());
      transport.sendMessage(message, message.getAllRecipients());
      return transport instanceof SMTPTransport smtp
          ? smtp.getLastServerResponse().strip()
          : "(đã gửi)";
    }
  }

  /** "nvu45823@gmail.com" → "nv••••••@gmail.com" */
  static String mask(String email) {
    int at = email.indexOf('@');
    if (at <= 0) return "•••";
    String local = email.substring(0, at);
    String shown = local.substring(0, Math.min(2, local.length()));
    return shown + "•".repeat(Math.max(1, local.length() - shown.length())) + email.substring(at);
  }

  private long minutes() {
    return properties.ttl().toMinutes();
  }

  private String plainText(String name, String code, boolean register) {
    return "Xin chào "
        + (name == null || name.isBlank() ? "bạn" : name)
        + ",\n\n"
        + (register
            ? "Mã xác thực để hoàn tất đăng ký tài khoản SmartTravel của bạn là: "
            : "Mã để đặt lại mật khẩu tài khoản SmartTravel của bạn là: ")
        + code
        + "\nMã có hiệu lực trong "
        + minutes()
        + " phút. Đừng chia sẻ mã này cho bất kỳ ai.\n\n"
        + "Nếu bạn không yêu cầu, hãy bỏ qua email này.\n\nSmartTravel";
  }

  private String html(String name, String code, boolean register) {
    String greeting = HtmlUtils.htmlEscape(name == null || name.isBlank() ? "bạn" : name);
    String intro =
        register
            ? "Dùng mã dưới đây để xác thực email và hoàn tất đăng ký tài khoản SmartTravel."
            : "Dùng mã dưới đây để đặt lại mật khẩu tài khoản SmartTravel của bạn.";
    String spaced = String.join(" ", code.split(""));
    return """
        <!doctype html>
        <html lang="vi"><body style="margin:0;padding:0;background:#f3f6f8;font-family:Arial,Helvetica,sans-serif;color:#13364a">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f6f8;padding:32px 12px">
          <tr><td align="center">
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e3e9ee">
              <tr><td style="background:#0d9488;padding:20px 28px">
                <span style="display:inline-block;width:36px;height:36px;border-radius:10px;background:#ffffff;color:#0d9488;font-size:20px;line-height:36px;text-align:center;vertical-align:middle">&#127759;</span>
                <span style="font-size:20px;font-weight:800;color:#ffffff;vertical-align:middle;margin-left:10px">Smart<span style="color:#99f6e4">Travel</span></span>
              </td></tr>
              <tr><td style="padding:28px">
                <p style="margin:0 0 8px;font-size:16px;font-weight:700">Xin chào %s,</p>
                <p style="margin:0 0 22px;font-size:14px;line-height:21px;color:#4f5f6b">%s</p>
                <div style="text-align:center;background:#effaf8;border:1px dashed #5eead4;border-radius:12px;padding:18px 0;font-size:32px;font-weight:800;letter-spacing:6px;color:#0f766e">%s</div>
                <p style="margin:18px 0 0;font-size:13px;line-height:20px;color:#4f5f6b">Mã có hiệu lực trong <b>%d phút</b>. Vì an toàn tài khoản, đừng chia sẻ mã này cho bất kỳ ai.</p>
                <p style="margin:14px 0 0;font-size:12px;line-height:18px;color:#8a99a6">Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email — tài khoản của bạn vẫn an toàn.</p>
              </td></tr>
              <tr><td style="padding:14px 28px;border-top:1px solid #eef2f5;font-size:11px;color:#8a99a6">SmartTravel · Gợi ý lịch trình du lịch Việt Nam</td></tr>
            </table>
          </td></tr>
        </table>
        </body></html>
        """
        .formatted(greeting, intro, spaced, minutes());
  }
}
