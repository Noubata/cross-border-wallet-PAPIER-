package com.example.crossborder.fx.api;

import java.math.BigDecimal;
import java.util.UUID;

public interface FxQuoteService {
    FxQuote createQuote(UUID customerId, String sourceCurrency, String destinationCurrency,
                        BigDecimal sourceAmount);

    FxQuote consumeQuote(UUID customerId, UUID quoteId);

    UUID getSystemAccount(String currency, String accountType);
}