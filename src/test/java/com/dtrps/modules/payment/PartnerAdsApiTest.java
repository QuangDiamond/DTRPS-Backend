package com.dtrps.modules.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dtrps.ApiTestBase;
import com.dtrps.modules.payment.service.PayOsGateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration tests for Partner Ads, PayOS Payments, Ad Packages, Ad Slots & Featured Places
 * (Report 5.2).
 */
class PartnerAdsApiTest extends ApiTestBase {

  @MockitoBean private PayOsGateway payOsGateway;

  @Test
  @DisplayName("admin manages ad packages, partner checks out, reconciles, picks slots & featured")
  void partnerAdsAndPayments_endToEnd() throws Exception {
    String admin = adminToken();
    String partner = partnerToken();
    String traveller = travellerToken();

    // 1. Admin creates, updates and toggles ad package; non-admin forbidden
    postAs(
            "/api/admin/ad-packages",
            traveller,
            Map.of(
                "name", "Forbidden",
                "price", 100000,
                "durationDays", 30,
                "maxPlaces", 2,
                "sortOrder", 1))
        .andExpect(status().isForbidden());

    String pkgName = "Gói Test " + UUID.randomUUID().toString().substring(0, 6);
    JsonNode createdPkg =
        json(
            postAs(
                    "/api/admin/ad-packages",
                    admin,
                    Map.of(
                        "name", pkgName,
                        "description", "Gói quảng cáo 30 ngày",
                        "price", 100000,
                        "durationDays", 30,
                        "maxPlaces", 2,
                        "sortOrder", 99))
                .andExpect(status().isCreated()));
    long pkgId = createdPkg.get("id").asLong();

    putAs(
            "/api/admin/ad-packages/" + pkgId,
            admin,
            Map.of(
                "name", pkgName,
                "description", "Gói quảng cáo cập nhật",
                "price", 120000,
                "durationDays", 30,
                "maxPlaces", 2,
                "sortOrder", 99))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(120000));

    // Disable then re-enable package
    putAs("/api/admin/ad-packages/" + pkgId + "/status", admin, Map.of("status", "INACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
    putAs("/api/admin/ad-packages/" + pkgId + "/status", admin, Map.of("status", "ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    // 2. Partner registers ACTIVE profile + APPROVED listing
    String taxCode =
        String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
    JsonNode profile =
        json(
            postAs(
                    "/api/business-profile",
                    partner,
                    Map.of(
                        "businessName", "Quán Quảng Cáo",
                        "taxCode", taxCode,
                        "contactEmail", "ads@partner.vn",
                        "contactPhone", "0912345678"))
                .andExpect(status().isCreated()));
    postAs("/api/admin/business-profiles/" + profile.get("id").asText() + "/approve", admin, null)
        .andExpect(status().isOk());

    String placeName = "Cafe Nổi Bật " + UUID.randomUUID().toString().substring(0, 6);
    JsonNode listing =
        json(
            postAs(
                    "/api/business-listings",
                    partner,
                    Map.of(
                        "name", placeName,
                        "categoryCode", "cafe",
                        "provinceCode", "hanoi",
                        "address", "20 Lý Thường Kiệt",
                        "latitude", 21.02,
                        "longitude", 105.85))
                .andExpect(status().isCreated()));
    long listingId = listing.get("id").asLong();
    postAs("/api/admin/business-listings/" + listingId + "/approve", admin, null)
        .andExpect(status().isOk());

    // 3. Partner views packages, checks out, then cancels pending order
    when(payOsGateway.createLink(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyLong()))
        .thenReturn(new PayOsGateway.PaymentLink("link-1", "https://pay.payos.vn/web/link-1"));
    when(payOsGateway.getStatus(anyLong())).thenReturn(new PayOsGateway.OrderStatus("PENDING", 0));

    getAs("/api/partner/ad-packages", partner).andExpect(status().isOk());
    getAs("/api/partner/ad-subscription", partner).andExpect(status().isOk());

    JsonNode checkout1 =
        json(
            postAs("/api/partner/ad-subscription/checkout", partner, Map.of("packageId", pkgId))
                .andExpect(status().isCreated()));
    String orderCode1 = checkout1.get("orderCode").asText();

    getAs("/api/partner/payments", partner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    getAs("/api/partner/payments/" + orderCode1, partner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));

    postAs("/api/partner/payments/" + orderCode1 + "/cancel", partner, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    // 4. Partner checks out again, admin reconciles PAID → subscription ACTIVE
    JsonNode checkout2 =
        json(
            postAs("/api/partner/ad-subscription/checkout", partner, Map.of("packageId", pkgId))
                .andExpect(status().isCreated()));
    long orderCode2 = Long.parseLong(checkout2.get("orderCode").asText());

    when(payOsGateway.getStatus(orderCode2))
        .thenReturn(new PayOsGateway.OrderStatus("PAID", 120000L));

    JsonNode adminPayments =
        json(
            getAs("/api/admin/payments?keyword=" + orderCode2, admin)
                .andExpect(status().isOk()));
    long paymentId = adminPayments.get("items").get(0).get("id").asLong();

    getAs("/api/admin/payments/stats", admin).andExpect(status().isOk());
    getAs("/api/admin/payments/" + paymentId, admin).andExpect(status().isOk());

    postAs("/api/admin/payments/" + paymentId + "/reconcile", admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    // 5. Forged PayOS webhook signature is rejected
    when(payOsGateway.verifyWebhook(any(), anyString())).thenReturn(false);
    postAs(
            "/api/payments/payos/webhook",
            null,
            Map.of("code", "00", "signature", "forged", "data", Map.of("orderCode", orderCode2)))
        .andExpect(status().isBadRequest());

    // 6. Partner selects ad slot → place appears in public /api/featured-places
    getAs("/api/partner/ad-slots", partner)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxPlaces").value(2));

    putAs("/api/partner/ad-slots", partner, Map.of("listingIds", List.of(listingId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.places[0].selected").value(true));

    getAs("/api/featured-places?limit=8", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == '" + placeName + "')]").exists());

    // Traveller cannot access partner ad endpoints
    getAs("/api/partner/ad-subscription", traveller).andExpect(status().isForbidden());
    getAs("/api/partner/ad-slots", traveller).andExpect(status().isForbidden());
  }
}
