package com.example.crossborder.funding.infrastructure;

import com.example.crossborder.funding.api.GatewayInitialization;
import com.example.crossborder.funding.api.PaymentGateway;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "funding.gateway", havingValue = "paystack")
public class PaystackTestPaymentGateway implements PaymentGateway {

    private final RestClient restClient;
    private final String secretKey;
    private final String webhookSecret;

    public PaystackTestPaymentGateway(RestClient.Builder restClientBuilder,
                                     @Value("${funding.paystack.base-url:https://api.paystack.co}") String baseUrl,
                                     @Value("${funding.paystack.secret-key:}") String secretKey,
                                     @Value("${funding.webhook-secret:}") String webhookSecret) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Paystack test secret key must be configured");
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("Funding webhook secret must be configured");
        }
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public GatewayInitialization initializePayment(String reference, long amountMinor,
                                                   String currency, String email) {
        JsonNode response = restClient.post()
                .uri("/transaction/initialize")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + secretKey)
                .body(Map.of("email", email, "amount", amountMinor, "currency", currency,
                        "reference", reference))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.path("status").asBoolean()) {
            throw new IllegalStateException("Paystack did not initialize the test transaction");
        }
        JsonNode data = response.path("data");
        return new GatewayInitialization(data.path("reference").asText(reference),
                data.path("authorization_url").asText());
    }

    @Override
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        return HmacSignatureVerifier.verifySha512(rawBody, signature, webhookSecret);
    }

    @Override
    public String gatewayName() {
        return "PAYSTACK_TEST";
    }
}