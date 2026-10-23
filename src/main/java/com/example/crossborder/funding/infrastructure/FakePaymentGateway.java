package com.example.crossborder.funding.infrastructure;

import com.example.crossborder.funding.api.GatewayInitialization;
import com.example.crossborder.funding.api.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
@ConditionalOnProperty(name = "funding.gateway", havingValue = "fake", matchIfMissing = true)
public class FakePaymentGateway implements PaymentGateway {

    private final String webhookSecret;

    public FakePaymentGateway(@Value("${funding.webhook-secret:local-funding-webhook-secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    public GatewayInitialization initializePayment(String reference, long amountMinor,
                                                   String currency, String email) {
        return new GatewayInitialization(reference, "https://sandbox.invalid/pay/" + reference);
    }

    @Override
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        return HmacSignatureVerifier.verifySha512(rawBody, signature, webhookSecret);
    }

    @Override
    public String gatewayName() {
        return "FAKE";
    }

    public String signWebhook(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA512 is unavailable", exception);
        }
    }
}