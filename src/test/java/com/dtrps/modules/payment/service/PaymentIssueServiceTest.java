package com.dtrps.modules.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.businessowner.entity.BusinessProfile;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.payment.dto.AdDtos;
import com.dtrps.modules.payment.entity.Payment;
import com.dtrps.modules.payment.entity.PaymentIssue;
import com.dtrps.modules.payment.entity.Subscription;
import com.dtrps.modules.payment.repository.PaymentIssueRepository;
import com.dtrps.modules.payment.repository.PaymentRepository;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

/** Unit test PaymentIssueService — sheet Payment, UTC058–UTC064 trong Report5.1 Unit Test. */
@ExtendWith(MockitoExtension.class)
class PaymentIssueServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Long ADMIN_ID = 1L;
  private static final Long PARTNER_ID = 7L;
  private static final long ORDER = 100_010L;
  private static final String RECEIPT = "https://res.cloudinary.com/f3wrjuzb/image/upload/a.jpg";
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Mock private PaymentIssueRepository issues;
  @Mock private PaymentRepository payments;
  @Mock private UserRepository users;
  @Mock private AdPaymentSyncService sync;
  @Mock private AdPaymentResultService results;
  @Mock private AuditService auditService;
  @Mock private NotificationService notifications;
  @Mock private PlatformTransactionManager transactionManager;

  private PaymentIssueService service;
  private User partner;
  private Payment payment;

  @BeforeEach
  void setUp() {
    service =
        new PaymentIssueService(
            issues,
            payments,
            users,
            sync,
            results,
            auditService,
            notifications,
            transactionManager,
            Clock.fixed(NOW, ZoneOffset.UTC));
    partner = new User();
    partner.setUserId(PARTNER_ID);
    BusinessProfile business = new BusinessProfile();
    business.setBusinessId(3L);
    business.setBusinessName("Cty Du lịch A");
    business.setUser(partner);
    Subscription subscription = new Subscription();
    subscription.setBusiness(business);
    subscription.setPackageName("Gói cơ bản");
    payment = new Payment();
    payment.setPaymentId(50L);
    payment.setUser(partner);
    payment.setSubscription(subscription);
    payment.setTransactionReference(String.valueOf(ORDER));
    payment.setAmount(new BigDecimal("100000"));
    payment.setStatus("CANCELLED");
    lenient()
        .when(payments.findByPaymentGatewayAndTransactionReference("PAYOS", String.valueOf(ORDER)))
        .thenReturn(Optional.of(payment));
    // JPA gán id khi lưu; thông báo cho admin cần id phiếu
    lenient()
        .when(issues.saveAndFlush(any(PaymentIssue.class)))
        .thenAnswer(
            call -> {
              PaymentIssue saved = call.getArgument(0);
              saved.setIssueId(9L);
              return saved;
            });
  }

  private static AdDtos.ReportIssueRequest report(String amount, String receiptUrl) {
    return new AdDtos.ReportIssueRequest(
        new BigDecimal(amount), NOW.minusSeconds(3600), " FT123 ", "  ", receiptUrl);
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
  @DisplayName("report")
  class Report {
    @Test
    @DisplayName("UTC058 Đơn đã huỷ, chưa có phiếu: hỏi lại PayOS rồi tạo phiếu OPEN, báo admin")
    void cancelledOrder_createsIssue() {
      AdDtos.ReportIssueRequest request = report("100000", RECEIPT);
      assertThat(messages(request)).isEmpty();

      service.report(PARTNER_ID, ORDER, request);

      verify(sync).reconcile(ORDER);
      ArgumentCaptor<PaymentIssue> captor = ArgumentCaptor.forClass(PaymentIssue.class);
      verify(issues).saveAndFlush(captor.capture());
      PaymentIssue issue = captor.getValue();
      assertThat(issue.getStatus()).isEqualTo("OPEN");
      assertThat(issue.getBankReference()).isEqualTo("FT123");
      assertThat(issue.getNote()).isNull();
      ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
      verify(notifications).notifyAdmins(notice.capture(), isNull());
      assertThat(notice.getValue().type()).isEqualTo("AD_PAYMENT_ISSUE_CREATED");
    }

    @Test
    @DisplayName("UTC059 Đơn đã có phiếu OPEN → 409")
    void alreadyOpen_conflict() {
      when(issues.existsByPaymentPaymentIdAndStatus(50L, "OPEN")).thenReturn(true);

      ApiException e = error(() -> service.report(PARTNER_ID, ORDER, report("100000", RECEIPT)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Đơn này đã có phiếu đang chờ xử lý");
      verify(issues, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UTC060 Đơn đã SUCCESS → 409, không hỏi PayOS")
    void paidOrder_conflict() {
      payment.setStatus("SUCCESS");

      ApiException e = error(() -> service.report(PARTNER_ID, ORDER, report("100000", RECEIPT)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Đơn đã được xác nhận thanh toán");
      verify(sync, never()).reconcile(anyLong());
    }

    @Test
    @DisplayName("UTC061 Số tiền 0 → 400 (validation)")
    void zeroAmount_invalid() {
      assertThat(messages(report("0", RECEIPT))).containsExactly("Số tiền phải lớn hơn 0");
    }

    @Test
    @DisplayName("UTC062 Ảnh biên lai không phải Cloudinary → 400 (validation)")
    void foreignReceipt_invalid() {
      assertThat(messages(report("100000", "https://abc.com/a.jpg")))
          .containsExactly("Ảnh biên lai không hợp lệ");
    }

    @Test
    @DisplayName("Hỏi lại PayOS thấy tiền đã về → 409, không tạo phiếu")
    void paidAfterReconcile_conflict() {
      doAnswer(
              call -> {
                payment.setStatus("SUCCESS");
                return null;
              })
          .when(sync)
          .reconcile(ORDER);

      ApiException e = error(() -> service.report(PARTNER_ID, ORDER, report("100000", RECEIPT)));

      assertThat(e.getMessage()).isEqualTo("Đơn đã được xác nhận thanh toán");
      verify(issues, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("PayOS tạm lỗi vẫn cho gửi phiếu")
    void gatewayError_stillReports() {
      doAnswer(
              call -> {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "x");
              })
          .when(sync)
          .reconcile(ORDER);

      service.report(PARTNER_ID, ORDER, report("100000", RECEIPT));

      verify(issues).saveAndFlush(any(PaymentIssue.class));
    }
  }

  @Nested
  @DisplayName("reject")
  class Reject {
    private PaymentIssue issue;

    @BeforeEach
    void openIssue() {
      issue = new PaymentIssue();
      issue.setIssueId(9L);
      issue.setPayment(payment);
      lenient().when(issues.lockById(9L)).thenReturn(Optional.of(issue));
    }

    @Test
    @DisplayName("UTC063 Phiếu OPEN: REJECTED kèm lý do, báo đối tác, làm mới tab admin")
    void open_rejects() {
      User admin = new User();
      when(users.getReferenceById(ADMIN_ID)).thenReturn(admin);

      service.reject(ADMIN_ID, 9L, " Không thấy tiền trong sao kê ");

      assertThat(issue.getStatus()).isEqualTo("REJECTED");
      assertThat(issue.getResolutionNote()).isEqualTo("Không thấy tiền trong sao kê");
      assertThat(issue.getHandledBy()).isSameAs(admin);
      assertThat(issue.getHandledAt()).isEqualTo(NOW);
      verify(notifications).notifyUser(eq(PARTNER_ID), any(Notice.class));
      verify(notifications).refreshAdmins(any(Notice.class));
    }

    @Test
    @DisplayName("UTC064 Phiếu đã RESOLVED → 409")
    void handled_conflict() {
      issue.setStatus("RESOLVED");

      ApiException e = error(() -> service.reject(ADMIN_ID, 9L, "x"));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Phiếu này đã được xử lý");
      verify(notifications, never()).notifyUser(anyLong(), any());
    }
  }
}
