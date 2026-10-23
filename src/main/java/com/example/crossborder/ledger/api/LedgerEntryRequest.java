package com.example.crossborder.ledger.api;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntryRequest(UUID accountId, BigDecimal debitAmount, BigDecimal creditAmount) {
}