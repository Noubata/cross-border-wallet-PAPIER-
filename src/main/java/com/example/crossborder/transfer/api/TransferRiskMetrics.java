package com.example.crossborder.transfer.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface TransferRiskMetrics {
    long countRecentTransfers(UUID customerId, Instant since);

    BigDecimal getDailyOutgoingTotal(UUID customerId, String currency, Instant dayStart);
}