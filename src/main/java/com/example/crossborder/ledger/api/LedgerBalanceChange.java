package com.example.crossborder.ledger.api;

import java.util.UUID;

public record LedgerBalanceChange(UUID accountId, long debitMinor, long creditMinor) {

    public long balanceDelta() {
        return creditMinor - debitMinor;
    }
}