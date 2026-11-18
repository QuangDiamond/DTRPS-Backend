package com.dtrps.modules.payment.service;

import com.fasterxml.jackson.databind.JsonNode;

/** Các API PayOS mà hệ thống dùng; test có thể thay bằng bản giả. */
public interface PayOsGateway {
  /** Link thanh toán vừa tạo */
  record PaymentLink(String paymentLinkId, String checkoutUrl) {}

  /** Trạng thái đơn trên PayOS; {@code amountPaid}: số tiền thực nhận */
  record OrderStatus(String status, long amountPaid) {}

  PaymentLink createLink(
      long orderCode,
      long amount,
      String description,
      String returnUrl,
      String cancelUrl,
      long expiredAtEpochSeconds);

  OrderStatus getStatus(long orderCode);

  void cancel(long orderCode, String reason);

  boolean verifyWebhook(JsonNode data, String signature);
}
