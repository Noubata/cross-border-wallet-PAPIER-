package com.example.crossborder.ledger.api;

import java.util.List;
import java.util.UUID;

public interface LedgerPostingService {
    UUID postTransaction(UUID operationId, String description, List<LedgerEntryRequest> entries);
}