package com.example.crossborder.wallet.domain;

import java.time.Instant;
import java.util.UUID;

public record Wallet(UUID id, UUID customerId, WalletCurrency currency,
                     UUID ledgerAccountId, long cachedBalanceMinor,
                     String status, Instant createdAt) {
}