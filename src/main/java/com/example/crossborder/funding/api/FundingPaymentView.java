package com.example.crossborder.funding.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FundingPaymentView(UUID id, String reference, UUID walletId, BigDecimal amount,
                                 String currency, String status, String authorizationUrl,
                                 Instant createdAt, Instant completedAt) {
}