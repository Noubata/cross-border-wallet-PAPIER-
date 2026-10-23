package com.example.crossborder.funding.domain;

import java.time.Instant;
import java.util.UUID;

public record FundingPayment(UUID id, UUID customerId, UUID walletId, UUID ledgerAccountId,
                             String gateway, String reference, long amountMinor,
                             String currency, String status, String authorizationUrl,
                             Instant createdAt, Instant completedAt) {
}