package com.example.crossborder.transfer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferRecord(UUID id, UUID customerId, UUID sourceWalletId, UUID destinationWalletId,
                             BigDecimal amount, String currency, BigDecimal destinationAmount,
                             String destinationCurrency, BigDecimal fee, UUID quoteId,
                             TransferState state, Instant createdAt, Instant completedAt) {

    public TransferRecord(UUID id, UUID customerId, UUID sourceWalletId, UUID destinationWalletId,
                          BigDecimal amount, String currency, BigDecimal fee,
                          TransferState state, Instant createdAt, Instant completedAt) {
        this(id, customerId, sourceWalletId, destinationWalletId, amount, currency, amount,
                currency, fee, null, state, createdAt, completedAt);
    }
}