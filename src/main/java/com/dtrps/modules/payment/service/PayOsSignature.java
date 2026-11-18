package com.dtrps.modules.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.StringJoiner;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Chữ ký HMAC-SHA256 của PayOS: khi tạo link thanh toán và khi kiểm tra webhook. */
public final class PayOsSignature {
  private static final String ALGORITHM = "HmacSHA256";

  private PayOsSignature() {}

  /** Chữ ký tạo link: các trường theo đúng thứ tự bảng chữ cái PayOS quy định. */
  public static String forPaymentRequest(
      long amount,
      String cancelUrl,
      String description,
      long orderCode,
      String returnUrl,
      String checksumKey) {
    String data =
        "amount="
            + amount
            + "&cancelUrl="
            + cancelUrl
            + "&description="
            + description
            + "&orderCode="
            + orderCode
            + "&returnUrl="
            + returnUrl;
    return hmac(data, checksumKey);
  }

  /** Webhook hợp lệ khi chữ ký khớp với object {@code data} (so sánh an toàn thời gian). */
  public static boolean verifyWebhook(JsonNode data, String signature, String checksumKey) {
    if (data == null || !data.isObject() || signature == null || signature.isBlank()) {
      return false;
    }
    String expected = hmac(canonical(data), checksumKey);
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
  }

  /** Sắp xếp key theo bảng chữ cái, nối key=value bằng &, giá trị rỗng thay cho null. */
  static String canonical(JsonNode data) {
    TreeMap<String, JsonNode> sorted = new TreeMap<>();
    data.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
    StringJoiner joined = new StringJoiner("&");
    sorted.forEach((key, value) -> joined.add(key + "=" + valueOf(value)));
    return joined.toString();
  }

  private static String valueOf(JsonNode value) {
    if (value == null || value.isNull() || value.isMissingNode()) {
      return "";
    }
    if (value.isContainerNode()) {
      return sortedJson(value);
    }
    String text = value.asText();
    return "null".equals(text) || "undefined".equals(text) ? "" : text;
  }

  /** Mảng / object lồng nhau: JSON với key đã sắp xếp */
  private static String sortedJson(JsonNode node) {
    if (node.isArray()) {
      List<String> items = new ArrayList<>();
      node.forEach(item -> items.add(sortedJson(item)));
      return "[" + String.join(",", items) + "]";
    }
    if (node.isObject()) {
      TreeMap<String, JsonNode> sorted = new TreeMap<>();
      node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
      List<String> items = new ArrayList<>();
      sorted.forEach((key, value) -> items.add("\"" + key + "\":" + sortedJson(value)));
      return "{" + String.join(",", items) + "}";
    }
    return node.toString();
  }

  private static String hmac(String data, String key) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Không tạo được chữ ký PayOS", e);
    }
  }
}
