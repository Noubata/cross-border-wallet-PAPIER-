package com.example.crossborder.ledger.api;

import java.util.UUID;

public interface LedgerAccountService {
    UUID createAccount(String accountType, String currency);
}