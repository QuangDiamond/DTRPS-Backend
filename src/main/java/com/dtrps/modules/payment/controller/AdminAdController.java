package com.dtrps.modules.payment.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.payment.dto.AdDtos;
import com.dtrps.modules.payment.service.AdPaymentSyncService;
import com.dtrps.modules.payment.service.AdminAdService;
import com.dtrps.modules.payment.service.PaymentIssueService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin quản lý bảng giá, giao dịch PayOS, gói đang chạy và quảng cáo của đối tác. */
@RestController
@Validated
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAdController {
  private static final int MAX_PAGE_SIZE = 100;

  private final AdminAdService adminService;
  private final AdPaymentSyncService sync;
  private final PaymentIssueService issueService;

  public AdminAdController(
      AdminAdService adminService, AdPaymentSyncService sync, PaymentIssueService issueService) {
    this.adminService = adminService;
    this.sync = sync;
    this.issueService = issueService;
  }

  // ===== Bảng giá =====

  @GetMapping("/ad-packages")
  public List<AdDtos.PackageItem> packages() {
    return adminService.packages();
  }

  @PostMapping("/ad-packages")
  @ResponseStatus(HttpStatus.CREATED)
  public AdDtos.PackageItem createPackage(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AdDtos.PackageRequest request) {
    return adminService.createPackage(CurrentUser.id(jwt), request);
  }

  @PutMapping("/ad-packages/{id}")
  public AdDtos.PackageItem updatePackage(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody AdDtos.PackageRequest request) {
    return adminService.updatePackage(CurrentUser.id(jwt), id, request);
  }

  @PutMapping("/ad-packages/{id}/status")
  public AdDtos.PackageItem changePackageStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody AdDtos.PackageStatusRequest request) {
    return adminService.changePackageStatus(CurrentUser.id(jwt), id, request.status());
  }

  // ===== Giao dịch =====

  @GetMapping("/payments/stats")
  public AdDtos.PaymentStats paymentStats() {
    return adminService.paymentStats();
  }

  @GetMapping("/payments")
  public PageResponse<AdDtos.AdminPayment> payments(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false)
          @Pattern(
              regexp = "^(PENDING|SUCCESS|FAILED|CANCELLED|REFUNDED)$",
              message = "Trạng thái giao dịch không hợp lệ")
          String status,
      @RequestParam(required = false)
          @Pattern(regexp = "^(INITIAL|RENEWAL)$", message = "Loại giao dịch không hợp lệ")
          String type,
      @RequestParam(defaultValue = "false") boolean review,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return adminService.payments(
        keyword, status, type, review, from, to, PageRequest.of(page, size, newest("paymentId")));
  }

  @GetMapping("/payments/{id}")
  public AdDtos.AdminPayment payment(@PathVariable Long id) {
    return adminService.payment(id);
  }

  /** Hỏi lại PayOS và áp dụng kết quả. */
  @PostMapping("/payments/{id}/reconcile")
  public AdDtos.AdminPayment reconcile(@PathVariable Long id) {
    sync.reconcile(adminService.orderCodeOf(id));
    return adminService.payment(id);
  }

  @PostMapping("/payments/{id}/refund")
  public AdDtos.AdminPayment refund(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody AdDtos.RefundRequest request) {
    return adminService.refund(CurrentUser.id(jwt), id, request.reason(), request.reference());
  }

  // ===== Sự cố thanh toán do đối tác báo =====

  @GetMapping("/payment-issues")
  public PageResponse<AdDtos.AdminPaymentIssue> paymentIssues(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false)
          @Pattern(
              regexp = "^(OPEN|RESOLVED|REJECTED)$",
              message = "Trạng thái phiếu không hợp lệ")
          String status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return issueService.list(keyword, status, PageRequest.of(page, size, newest("issueId")));
  }

  @PostMapping("/payment-issues/{id}/reconcile")
  public AdDtos.AdminPaymentIssue reconcileIssue(@PathVariable Long id) {
    return issueService.reconcile(id);
  }

  /** Chỉ dùng khi PayOS không ghi nhận nhưng sao kê ngân hàng có tiền; có ghi audit log. */
  @PostMapping("/payment-issues/{id}/activate")
  public AdDtos.AdminPaymentIssue activateIssue(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody AdDtos.ManualActivateRequest request) {
    return issueService.activate(
        CurrentUser.id(jwt), id, request.bankReference(), request.reason());
  }

  @PostMapping("/payment-issues/{id}/reject")
  public AdDtos.AdminPaymentIssue rejectIssue(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody AdDtos.ReasonRequest request) {
    return issueService.reject(CurrentUser.id(jwt), id, request.reason());
  }

  // ===== Gói đang chạy =====

  @GetMapping("/ad-subscriptions")
  public PageResponse<AdDtos.AdminSubscription> subscriptions(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false)
          @Pattern(
              regexp = "^(ACTIVE|EXPIRED|CANCELLED)$",
              message = "Trạng thái gói không hợp lệ")
          String status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return adminService.subscriptions(
        keyword, status, PageRequest.of(page, size, newest("subscriptionId")));
  }

  @PostMapping("/ad-subscriptions/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelSubscription(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody AdDtos.ReasonRequest request) {
    adminService.cancelSubscription(CurrentUser.id(jwt), id, request.reason());
  }

  // ===== Quảng cáo =====

  @GetMapping("/advertisements")
  public PageResponse<AdDtos.AdminAdvertisement> advertisements(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false)
          @Pattern(
              regexp = "^(PUBLISHED|SUSPENDED|EXPIRED)$",
              message = "Trạng thái quảng cáo không hợp lệ")
          String status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return adminService.advertisements(
        keyword, status, PageRequest.of(page, size, newest("advertisementId")));
  }

  @PutMapping("/advertisements/{id}/status")
  public AdDtos.AdminAdvertisement changeAdStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody AdDtos.AdStatusRequest request) {
    return adminService.changeAdStatus(
        CurrentUser.id(jwt), id, request.status(), request.reason());
  }

  private static Sort newest(String idField) {
    return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, idField));
  }
}
