package com.example.crossborder.funding.api;

public interface PaymentGateway {
    GatewayInitialization initializePayment(String reference, long amountMinor,
                                            String currency, String email);

    boolean verifyWebhookSignature(byte[] rawBody, String signature);

    String gatewayName();
}