package com.example.crossborder.wallet.api;

import java.util.UUID;

public record LockedWallet(UUID id, UUID customerId, String currency, UUID ledgerAccountId, String status) {
}