package com.dtrps.modules.payment.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.payment.dto.AdDtos;
import com.dtrps.modules.payment.entity.Payment;
import com.dtrps.modules.payment.entity.PaymentIssue;
import com.dtrps.modules.payment.entity.Subscription;
import com.dtrps.modules.payment.repository.PaymentIssueRepository;
import com.dtrps.modules.payment.repository.PaymentRepository;
import com.dtrps.modules.user.repository.UserRepository;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Đối tác báo đã chuyển tiền mà đơn chưa được ghi nhận; admin đối soát và xử lý. */
@Service
public class PaymentIssueService {
  static final String OPEN = "OPEN";
  static final String RESOLVED = "RESOLVED";
  static final String REJECTED = "REJECTED";
  private static final String AUDIT_ENTITY = "payment_issues";
  private static final String ISSUE_NOT_FOUND = "Không tìm thấy phiếu sự cố";
  private static final String ALREADY_OPEN = "Đơn này đã có phiếu đang chờ xử lý";
  private static final String ALREADY_PAID = "Đơn đã được xác nhận thanh toán";

  private final PaymentIssueRepository issues;
  private final PaymentRepository payments;
  private final UserRepository users;
  private final AdPaymentSyncService sync;
  private final AdPaymentResultService results;
  private final AuditService auditService;
  private final NotificationService notifications;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public PaymentIssueService(
      PaymentIssueRepository issues,
      PaymentRepository payments,
      UserRepository users,
      AdPaymentSyncService sync,
      AdPaymentResultService results,
      AuditService auditService,
      NotificationService notifications,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.issues = issues;
    this.payments = payments;
    this.users = users;
    this.sync = sync;
    this.results = results;
    this.auditService = auditService;
    this.notifications = notifications;
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  // ===== Đối tác =====

  /** Hỏi lại PayOS trước: tiền đã về thì gói tự kích hoạt, không cần tạo phiếu. */
  public void report(Long userId, long orderCode, AdDtos.ReportIssueRequest request) {
    requireReportable(ownedPayment(userId, orderCode));
    try {
      sync.reconcile(orderCode);
    } catch (ApiException e) {
      // PayOS tạm lỗi vẫn cho gửi phiếu: admin sẽ đối soát lại sau
    }
    Payment payment = ownedPayment(userId, orderCode);
    requireReportable(payment);
    try {
      transaction.executeWithoutResult(status -> create(userId, payment, request));
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(HttpStatus.CONFLICT, ALREADY_OPEN);
    }
  }

  private void create(Long userId, Payment payment, AdDtos.ReportIssueRequest request) {
    if (issues.existsByPaymentPaymentIdAndStatus(payment.getPaymentId(), OPEN)) {
      throw new ApiException(HttpStatus.CONFLICT, ALREADY_OPEN);
    }
    PaymentIssue issue = new PaymentIssue();
    issue.setPayment(payment);
    issue.setReporter(users.getReferenceById(userId));
    issue.setTransferredAmount(request.transferredAmount());
    issue.setTransferredAt(request.transferredAt());
    issue.setBankReference(request.bankReference().trim());
    issue.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
    issue.setReceiptUrl(request.receiptUrl().trim());
    issues.saveAndFlush(issue);
    notifications.notifyAdmins(
        new Notice(
            "AD_PAYMENT_ISSUE_CREATED",
            "Đối tác báo sự cố thanh toán",
            payment.getSubscription().getBusiness().getBusinessName()
                + " báo đã chuyển tiền cho đơn #"
                + payment.getTransactionReference()
                + ".",
            "/admin/payments?tab=issues",
            Map.of("issueId", issue.getIssueId())),
        null);
  }

  private static void requireReportable(Payment payment) {
    if (AdPaymentResultService.SUCCESS.equals(payment.getStatus())
        || "REFUNDED".equals(payment.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, ALREADY_PAID);
    }
  }

  private Payment ownedPayment(Long userId, long orderCode) {
    return payments
        .findByPaymentGatewayAndTransactionReference(
            AdPaymentResultService.GATEWAY, String.valueOf(orderCode))
        .filter(
            payment ->
                payment.getSubscription().getBusiness().getUser().getUserId().equals(userId))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn thanh toán"));
  }

  // ===== Admin =====

  @Transactional(readOnly = true)
  public PageResponse<AdDtos.AdminPaymentIssue> list(
      String keyword, String status, Pageable pageable) {
    Specification<PaymentIssue> spec = (root, query, cb) -> cb.conjunction();
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) -> {
                var payment = root.join("payment");
                return cb.or(
                    cb.like(cb.lower(payment.get("transactionReference")), like),
                    cb.like(cb.lower(root.get("bankReference")), like),
                    cb.like(
                        cb.lower(payment.join("subscription").join("business").get("businessName")),
                        like));
              });
    }
    if (status != null && !status.isBlank()) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    return PageResponse.from(issues.findAll(spec, pageable).map(PaymentIssueService::toAdmin));
  }

  /** Hỏi lại PayOS. Nếu PayOS xác nhận đã trả, gói tự kích hoạt và phiếu tự đóng. */
  public AdDtos.AdminPaymentIssue reconcile(Long issueId) {
    PaymentIssue issue = requireDetails(issueId);
    sync.reconcile(Long.parseLong(issue.getPayment().getTransactionReference()));
    return toAdmin(requireDetails(issueId));
  }

  /** PayOS không ghi nhận nhưng admin đã thấy tiền trong sao kê ngân hàng. */
  public AdDtos.AdminPaymentIssue activate(
      Long adminId, Long issueId, String bankReference, String reason) {
    results.activateFromIssue(adminId, issueId, bankReference.trim(), reason.trim());
    return toAdmin(requireDetails(issueId));
  }

  @Transactional
  public AdDtos.AdminPaymentIssue reject(Long adminId, Long issueId, String reason) {
    PaymentIssue issue =
        issues
            .lockById(issueId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ISSUE_NOT_FOUND));
    if (!OPEN.equals(issue.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, "Phiếu này đã được xử lý");
    }
    issue.setStatus(REJECTED);
    issue.setResolutionNote(reason.trim());
    issue.setHandledBy(users.getReferenceById(adminId));
    issue.setHandledAt(clock.instant());
    auditService.record(
        adminId, "AD_PAYMENT_ISSUE_REJECTED", AUDIT_ENTITY, issueId, reason.trim());
    Payment payment = issue.getPayment();
    Notice notice =
        new Notice(
            "AD_PAYMENT_ISSUE_REJECTED",
            "Phiếu sự cố thanh toán bị từ chối",
            "Đơn #" + payment.getTransactionReference() + ". Lý do: " + reason.trim(),
            "/partner/ads",
            Map.of("issueId", issueId));
    notifications.notifyUser(payment.getUser().getUserId(), notice);
    // Số đếm phiếu chờ xử lý trên menu của các tab admin
    notifications.refreshAdmins(notice);
    return toAdmin(issue);
  }

  private PaymentIssue requireDetails(Long issueId) {
    return issues
        .findWithDetailsByIssueId(issueId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ISSUE_NOT_FOUND));
  }

  private static AdDtos.AdminPaymentIssue toAdmin(PaymentIssue issue) {
    Payment payment = issue.getPayment();
    Subscription subscription = payment.getSubscription();
    return new AdDtos.AdminPaymentIssue(
        issue.getIssueId(),
        payment.getPaymentId(),
        payment.getTransactionReference(),
        payment.getStatus(),
        subscription.getBusiness().getBusinessId(),
        subscription.getBusiness().getBusinessName(),
        subscription.getPackageName(),
        payment.getAmount(),
        issue.getTransferredAmount(),
        issue.getTransferredAt(),
        issue.getBankReference(),
        issue.getNote(),
        issue.getReceiptUrl(),
        issue.getStatus(),
        issue.getResolutionNote(),
        issue.getHandledAt(),
        issue.getCreatedAt());
  }
}
