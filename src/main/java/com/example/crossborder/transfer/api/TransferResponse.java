package com.example.crossborder.transfer.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID id, UUID sourceWalletId, UUID destinationWalletId,
                               BigDecimal amount, String currency, BigDecimal fee,
                               String state, String failureCode, Instant createdAt,
                               Instant completedAt, BigDecimal destinationAmount,
                               String destinationCurrency, UUID quoteId) {

    public TransferResponse(UUID id, UUID sourceWalletId, UUID destinationWalletId,
                            BigDecimal amount, String currency, BigDecimal fee,
                            String state, String failureCode, Instant createdAt, Instant completedAt) {
        this(id, sourceWalletId, destinationWalletId, amount, currency, fee, state, failureCode,
                createdAt, completedAt, amount, currency, null);
    }
}