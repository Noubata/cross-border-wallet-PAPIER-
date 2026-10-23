package com.example.crossborder.wallet.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletView(UUID id, String currency, BigDecimal balance, String status, Instant createdAt) {
}