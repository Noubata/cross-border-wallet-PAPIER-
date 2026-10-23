package com.example.crossborder.transfer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferRecord(UUID id, UUID customerId, UUID sourceWalletId, UUID destinationWalletId,
                             BigDecimal amount, String currency, BigDecimal fee,
                             TransferState state, Instant createdAt, Instant completedAt) {
}