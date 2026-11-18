package com.dtrps.modules.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dtrps.modules.payment.service.PayOsSignature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PayOsSignatureTest {
  private static final String KEY = "test-checksum-key";
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void paymentRequestSignsFieldsInDocumentedOrder() throws Exception {
    String expected =
        hmac("amount=100000&cancelUrl=http://c&description=ST100001&orderCode=100001&returnUrl=http://r");

    assertEquals(
        expected,
        PayOsSignature.forPaymentRequest(100000, "http://c", "ST100001", 100001, "http://r", KEY));
  }

  @Test
  void webhookSortsKeysAndTreatsNullAsEmpty() throws Exception {
    JsonNode data = mapper.readTree("{\"orderCode\":123,\"amount\":3000,\"reference\":null}");
    String signature = hmac("amount=3000&orderCode=123&reference=");

    assertTrue(PayOsSignature.verifyWebhook(data, signature, KEY));
  }

  @Test
  void webhookRejectsTamperedAmount() throws Exception {
    String signature = hmac("amount=3000&orderCode=123");
    JsonNode tampered = mapper.readTree("{\"orderCode\":123,\"amount\":1}");

    assertFalse(PayOsSignature.verifyWebhook(tampered, signature, KEY));
  }

  @Test
  void webhookRejectsMissingSignature() throws Exception {
    assertFalse(PayOsSignature.verifyWebhook(mapper.readTree("{\"a\":1}"), null, KEY));
  }

  private static String hmac(String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
  }
}
