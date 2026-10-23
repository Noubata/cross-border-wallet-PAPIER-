package com.example.crossborder.ledger.api;

import java.util.List;
import java.util.UUID;

public record LedgerTransactionPosted(UUID transactionId, UUID operationId, String currency,
                                      List<LedgerBalanceChange> movements) {
}