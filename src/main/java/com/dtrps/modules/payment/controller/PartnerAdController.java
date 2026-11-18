package com.dtrps.modules.payment.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.payment.dto.AdDtos;
import com.dtrps.modules.payment.service.PartnerAdService;
import com.dtrps.modules.payment.service.PaymentIssueService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.data.domain.PageRequest;
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

/** Đối tác mua gói quảng cáo, thanh toán PayOS và chọn địa điểm được quảng cáo. */
@RestController
@Validated
@RequestMapping("/api/partner")
@PreAuthorize("hasRole('BUSINESS_OWNER')")
public class PartnerAdController {
  private final PartnerAdService adService;
  private final PaymentIssueService issueService;

  public PartnerAdController(PartnerAdService adService, PaymentIssueService issueService) {
    this.adService = adService;
    this.issueService = issueService;
  }

  @GetMapping("/ad-packages")
  public List<AdDtos.PackageItem> packages() {
    return adService.activePackages();
  }

  @GetMapping("/ad-subscription")
  public AdDtos.PartnerState state(@AuthenticationPrincipal Jwt jwt) {
    return adService.state(CurrentUser.id(jwt));
  }

  @PostMapping("/ad-subscription/checkout")
  @ResponseStatus(HttpStatus.CREATED)
  public AdDtos.CheckoutResponse checkout(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AdDtos.CheckoutRequest request) {
    return adService.checkout(CurrentUser.id(jwt), request.packageId());
  }

  @PostMapping("/ad-subscription/renew")
  @ResponseStatus(HttpStatus.CREATED)
  public AdDtos.CheckoutResponse renew(@AuthenticationPrincipal Jwt jwt) {
    return adService.renew(CurrentUser.id(jwt));
  }

  @GetMapping("/payments")
  public PageResponse<AdDtos.PaymentItem> payments(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
    return adService.payments(CurrentUser.id(jwt), PageRequest.of(page, size));
  }

  @GetMapping("/payments/{orderCode}")
  public AdDtos.PaymentItem payment(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long orderCode) {
    return adService.paymentStatus(CurrentUser.id(jwt), orderCode);
  }

  @PostMapping("/payments/{orderCode}/cancel")
  public AdDtos.PaymentItem cancel(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long orderCode) {
    return adService.cancelPayment(CurrentUser.id(jwt), orderCode);
  }

  /** "Tôi đã thanh toán, kiểm tra lại": hỏi lại PayOS ngay */
  @PostMapping("/payments/{orderCode}/check")
  public AdDtos.PaymentItem check(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long orderCode) {
    return adService.checkPayment(CurrentUser.id(jwt), orderCode);
  }

  @PostMapping("/payments/{orderCode}/issues")
  @ResponseStatus(HttpStatus.CREATED)
  public void reportIssue(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long orderCode,
      @Valid @RequestBody AdDtos.ReportIssueRequest request) {
    issueService.report(CurrentUser.id(jwt), orderCode, request);
  }

  @GetMapping("/ad-slots")
  public AdDtos.SlotsResponse slots(@AuthenticationPrincipal Jwt jwt) {
    return adService.slots(CurrentUser.id(jwt));
  }

  @PutMapping("/ad-slots")
  public AdDtos.SlotsResponse saveSlots(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AdDtos.SaveSlotsRequest request) {
    return adService.saveSlots(CurrentUser.id(jwt), request.listingIds());
  }
}
