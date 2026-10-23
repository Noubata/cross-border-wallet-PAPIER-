package com.example.crossborder.funding.infrastructure;

import com.example.crossborder.funding.api.GatewayInitialization;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakePaymentGatewayTest {

    @Test
    void initializesDeterministicSandboxCheckout() {
        FakePaymentGateway gateway = new FakePaymentGateway("test-secret");

        GatewayInitialization initialization = gateway.initializePayment("ref-123", 5000, "NGN", "user@example.com");

        assertEquals("ref-123", initialization.reference());
        assertEquals("https://sandbox.invalid/pay/ref-123", initialization.authorizationUrl());
        assertEquals("FAKE", gateway.gatewayName());
    }

    @Test
    void verifiesHmacSha512AndRejectsTamperedBodyOrSignature() {
        FakePaymentGateway gateway = new FakePaymentGateway("test-secret");
        byte[] body = "{\"event\":\"charge.success\"}".getBytes(StandardCharsets.UTF_8);
        String signature = gateway.signWebhook(body);

        assertTrue(gateway.verifyWebhookSignature(body, signature));
        assertFalse(gateway.verifyWebhookSignature("{}".getBytes(StandardCharsets.UTF_8), signature));
        assertFalse(gateway.verifyWebhookSignature(body, "invalid"));
        assertFalse(gateway.verifyWebhookSignature(body, ""));
    }
}