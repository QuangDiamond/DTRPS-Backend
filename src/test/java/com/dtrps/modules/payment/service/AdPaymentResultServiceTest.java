package com.dtrps.modules.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.businessowner.entity.BusinessProfile;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.payment.entity.Payment;
import com.dtrps.modules.payment.entity.PaymentIssue;
import com.dtrps.modules.payment.entity.Subscription;
import com.dtrps.modules.payment.entity.SubscriptionRenewalWindow;
import com.dtrps.modules.payment.repository.PaymentIssueRepository;
import com.dtrps.modules.payment.repository.PaymentRepository;
import com.dtrps.modules.payment.repository.SubscriptionRenewalWindowRepository;
import com.dtrps.modules.payment.repository.SubscriptionRepository;
import com.dtrps.modules.payment.service.AdPaymentResultService.GatewayResult;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import java.math.BigDecimal;
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

/** Unit test AdPaymentResultService — sheet Payment, UTC026–UTC037 trong Report5.1 Unit Test. */
@ExtendWith(MockitoExtension.class)
class AdPaymentResultServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final long ORDER = 100_010L;
  private static final Long PARTNER_ID = 7L;
  private static final Long ADMIN_ID = 1L;
  private static final Long BUSINESS_ID = 3L;

  @Mock private PaymentRepository payments;
  @Mock private SubscriptionRepository subscriptions;
  @Mock private SubscriptionRenewalWindowRepository windows;
  @Mock private PaymentIssueRepository issues;
  @Mock private UserRepository users;
  @Mock private AdSubscriptionOps subscriptionOps;
  @Mock private AuditService auditService;
  @Mock private NotificationService notifications;

  private AdPaymentResultService service;
  private Payment payment;
  private Subscription subscription;

  @BeforeEach
  void setUp() {
    service =
        new AdPaymentResultService(
            payments,
            subscriptions,
            windows,
            issues,
            users,
            subscriptionOps,
            auditService,
            notifications,
            Clock.fixed(NOW, ZoneOffset.UTC));

    User partner = new User();
    partner.setUserId(PARTNER_ID);
    BusinessProfile business = new BusinessProfile();
    business.setBusinessId(BUSINESS_ID);
    business.setBusinessName("Cty Du lịch A");
    business.setUser(partner);

    subscription = new Subscription();
    subscription.setSubscriptionId(20L);
    subscription.setBusiness(business);
    subscription.setPackageName("Gói cơ bản");
    subscription.setDurationDays(30);
    subscription.setMaxPlaces(3);
    subscription.setMonthlyFee(new BigDecimal("100000"));

    payment = new Payment();
    payment.setPaymentId(50L);
    payment.setUser(partner);
    payment.setSubscription(subscription);
    payment.setAmount(new BigDecimal("100000"));
    payment.setTransactionReference(String.valueOf(ORDER));
    payment.setPaymentType("INITIAL");

    lenient()
        .when(payments.lockByReference("PAYOS", String.valueOf(ORDER)))
        .thenReturn(Optional.of(payment));
    lenient().when(subscriptions.lockById(20L)).thenReturn(Optional.of(subscription));
  }

  private Notice lastAdminNotice() {
    ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
    verify(notifications).notifyAdmins(captor.capture(), isNull());
    return captor.getValue();
  }

  @Nested
  @DisplayName("apply")
  class Apply {
    @Test
    @DisplayName("UTC026 PAID đúng số tiền: đơn SUCCESS, gói ACTIVE 30 ngày tính từ lúc trả")
    void paid_activatesSubscription() {
      service.apply(ORDER, new GatewayResult("PAID", 100_000, "FT123"));

      assertThat(payment.getStatus()).isEqualTo("SUCCESS");
      assertThat(payment.getPaidAt()).isEqualTo(NOW);
      assertThat(payment.getBankReference()).isEqualTo("FT123");
      assertThat(subscription.getStatus()).isEqualTo("ACTIVE");
      assertThat(subscription.getStartDate()).isEqualTo(NOW);
      assertThat(subscription.getEndDate()).isEqualTo(NOW.plus(Duration.ofDays(30)));
      ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
      verify(notifications).notifyUser(eq(PARTNER_ID), captor.capture());
      assertThat(captor.getValue().type()).isEqualTo("AD_PAYMENT_SUCCESS");
      assertThat(lastAdminNotice().type()).isEqualTo("AD_PAYMENT_SUCCESS");
    }

    @Test
    @DisplayName("UTC027 Webhook gửi lần 2 cho đơn đã SUCCESS: bỏ qua, không kích hoạt lại")
    void paidTwice_isIgnored() {
      payment.setStatus("SUCCESS");

      service.apply(ORDER, new GatewayResult("PAID", 100_000, null));

      verify(subscriptions, never()).lockById(anyLong());
      verifyNoInteractions(notifications, auditService);
    }

    @Test
    @DisplayName("UTC028 PAID sai số tiền: không kích hoạt, đánh dấu cần kiểm tra, báo admin")
    void wrongAmount_flagsForReview() {
      service.apply(ORDER, new GatewayResult("PAID", 99_000, null));

      assertThat(payment.getStatus()).isEqualTo("PENDING");
      assertThat(payment.isNeedsReview()).isTrue();
      assertThat(payment.getReviewNote()).contains("Sai số tiền");
      assertThat(subscription.getStatus()).isEqualTo("PENDING");
      assertThat(lastAdminNotice().type()).isEqualTo("AD_PAYMENT_REVIEW");
    }

    @Test
    @DisplayName("UTC029 CANCELLED: đơn và đăng ký mua mới chuyển CANCELLED, báo đối tác")
    void cancelled_closesOrder() {
      service.apply(ORDER, new GatewayResult("CANCELLED", 0, null));

      assertThat(payment.getStatus()).isEqualTo("CANCELLED");
      assertThat(subscription.getStatus()).isEqualTo("CANCELLED");
      ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
      verify(notifications).notifyUser(eq(PARTNER_ID), captor.capture());
      assertThat(captor.getValue().type()).isEqualTo("AD_PAYMENT_CANCELLED");
    }

    @Test
    @DisplayName("UTC030 Trạng thái lạ: giữ PENDING, đánh dấu cần kiểm tra")
    void unknownStatus_flagsForReview() {
      service.apply(ORDER, new GatewayResult("UNKNOWN", 0, null));

      assertThat(payment.getStatus()).isEqualTo("PENDING");
      assertThat(payment.isNeedsReview()).isTrue();
      assertThat(payment.getReviewNote()).contains("UNKNOWN");
    }

    @Test
    @DisplayName("UTC031 Không có đơn (giao dịch mẫu khi đăng ký webhook): bỏ qua, không lỗi")
    void unknownOrder_isIgnored() {
      when(payments.lockByReference("PAYOS", "123")).thenReturn(Optional.empty());

      service.apply(123, new GatewayResult("PAID", 1_000, null));

      verifyNoInteractions(subscriptions, notifications, auditService);
    }

    @Test
    @DisplayName("UTC032 Trả muộn khi đối tác đã có gói khác: đơn SUCCESS nhưng không kích hoạt")
    void latePaymentWithOtherActive_needsRefund() {
      payment.setStatus("CANCELLED");
      subscription.setStatus("CANCELLED");
      when(subscriptions.existsByBusinessBusinessIdAndStatus(BUSINESS_ID, "ACTIVE"))
          .thenReturn(true);

      service.apply(ORDER, new GatewayResult("PAID", 100_000, null));

      assertThat(payment.getStatus()).isEqualTo("SUCCESS");
      assertThat(payment.isNeedsReview()).isTrue();
      assertThat(subscription.getStatus()).isEqualTo("CANCELLED");
      verify(notifications, never()).notifyUser(anyLong(), any());
      assertThat(lastAdminNotice().type()).isEqualTo("AD_PAYMENT_REVIEW");
    }

    @Test
    @DisplayName("Trả muộn khi chưa có gói nào: vẫn kích hoạt bình thường")
    void latePaymentWithoutOther_activates() {
      payment.setStatus("CANCELLED");
      subscription.setStatus("CANCELLED");

      service.apply(ORDER, new GatewayResult("PAID", 100_000, null));

      assertThat(payment.getStatus()).isEqualTo("SUCCESS");
      assertThat(subscription.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Gia hạn: cộng 30 ngày từ ngày hết hạn cũ, kéo dài quảng cáo, đóng cửa sổ")
    void renewal_extendsFromOldEndDate() {
      Instant oldEnd = NOW.plus(Duration.ofDays(3));
      subscription.setStatus("ACTIVE");
      subscription.setEndDate(oldEnd);
      payment.setPaymentType("RENEWAL");
      SubscriptionRenewalWindow window = new SubscriptionRenewalWindow();
      window.setStatus("OPEN");
      when(windows.findFirstBySubscriptionSubscriptionIdAndStatus(20L, "OPEN"))
          .thenReturn(Optional.of(window));

      service.apply(ORDER, new GatewayResult("PAID", 100_000, null));

      assertThat(subscription.getEndDate()).isEqualTo(oldEnd.plus(Duration.ofDays(30)));
      assertThat(window.getStatus()).isEqualTo("USED");
      verify(subscriptionOps).extendAds(subscription);
    }

    @Test
    @DisplayName("PENDING / PROCESSING: giữ nguyên, chờ kết quả tiếp")
    void processing_keepsPending() {
      service.apply(ORDER, new GatewayResult("PROCESSING", 0, null));

      assertThat(payment.getStatus()).isEqualTo("PENDING");
      assertThat(payment.isNeedsReview()).isFalse();
      verifyNoInteractions(notifications);
    }
  }

  @Nested
  @DisplayName("activateFromIssue")
  class ActivateFromIssue {
    private PaymentIssue issue;

    @BeforeEach
    void openIssue() {
      payment.setStatus("CANCELLED");
      subscription.setStatus("CANCELLED");
      issue = new PaymentIssue();
      issue.setIssueId(9L);
      issue.setPayment(payment);
      lenient().when(issues.lockById(9L)).thenReturn(Optional.of(issue));
    }

    @Test
    @DisplayName("UTC033 Phiếu OPEN, chưa có gói khác: kích hoạt, ghi audit thủ công")
    void manualActivation_activates() {
      User admin = new User();
      admin.setUserId(ADMIN_ID);
      when(users.getReferenceById(ADMIN_ID)).thenReturn(admin);
      when(issues.findFirstByPaymentPaymentIdAndStatus(50L, "OPEN")).thenReturn(Optional.of(issue));

      service.activateFromIssue(ADMIN_ID, 9L, "FT999", "Sao kê có tiền");

      assertThat(payment.getStatus()).isEqualTo("SUCCESS");
      assertThat(payment.getGatewayStatus()).isEqualTo("MANUAL");
      assertThat(payment.getBankReference()).isEqualTo("FT999");
      assertThat(subscription.getStatus()).isEqualTo("ACTIVE");
      assertThat(issue.getStatus()).isEqualTo("RESOLVED");
      assertThat(issue.getHandledBy()).isSameAs(admin);
      verify(auditService)
          .record(
              ADMIN_ID, "AD_PAYMENT_MANUAL_ACTIVATED", "payments", 50L, "FT999: Sao kê có tiền");
    }

    @Test
    @DisplayName("UTC034 Phiếu đã xử lý → 409")
    void handledIssue_conflict() {
      issue.setStatus("REJECTED");

      ApiException e =
          catchThrowableOfType(
              () -> service.activateFromIssue(ADMIN_ID, 9L, "FT999", "x"), ApiException.class);

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Phiếu này đã được xử lý");
    }

    @Test
    @DisplayName("UTC035 Đơn đã SUCCESS → 409")
    void paidOrder_conflict() {
      payment.setStatus("SUCCESS");

      ApiException e =
          catchThrowableOfType(
              () -> service.activateFromIssue(ADMIN_ID, 9L, "FT999", "x"), ApiException.class);

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Đơn này đã được ghi nhận thanh toán");
    }

    @Test
    @DisplayName("UTC036 Đối tác đang có gói khác → 409, không ghi audit thủ công")
    void otherActiveSubscription_conflict() {
      when(subscriptions.existsByBusinessBusinessIdAndStatus(BUSINESS_ID, "ACTIVE"))
          .thenReturn(true);

      ApiException e =
          catchThrowableOfType(
              () -> service.activateFromIssue(ADMIN_ID, 9L, "FT999", "x"), ApiException.class);

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage())
          .isEqualTo(
              "Không kích hoạt được: đối tác đang có gói khác hoặc gói cần gia hạn đã kết thúc");
      verify(auditService, never())
          .record(any(), eq("AD_PAYMENT_MANUAL_ACTIVATED"), any(), any(), any());
      verify(auditService, times(1)).record(any(), eq("AD_PAYMENT_SUCCESS"), any(), any(), any());
    }

    @Test
    @DisplayName("UTC037 Không có phiếu → 404")
    void missingIssue_notFound() {
      when(issues.lockById(999L)).thenReturn(Optional.empty());

      ApiException e =
          catchThrowableOfType(
              () -> service.activateFromIssue(ADMIN_ID, 999L, "FT999", "x"), ApiException.class);

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy phiếu sự cố");
    }
  }
}
