package com.example.crossborder.transfer.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID id, UUID sourceWalletId, UUID destinationWalletId,
                               BigDecimal amount, String currency, BigDecimal fee,
                               String state, String failureCode, Instant createdAt,
                               Instant completedAt) {
}