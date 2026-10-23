package com.example.crossborder.ledger.api;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerBalanceService {
    BigDecimal getBalance(UUID accountId);
}