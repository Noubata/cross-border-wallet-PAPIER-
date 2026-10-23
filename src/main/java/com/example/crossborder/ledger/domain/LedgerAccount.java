package com.example.crossborder.ledger.domain;

import java.util.UUID;

public record LedgerAccount(UUID id, String accountType, LedgerCurrency currency) {
}