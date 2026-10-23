package com.example.crossborder.fx.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FxQuote(UUID id, String sourceCurrency, String destinationCurrency,
                      BigDecimal sourceAmount, BigDecimal destinationAmount,
                      BigDecimal grossDestinationAmount, BigDecimal flatFee,
                      BigDecimal spreadFee, BigDecimal midRate, BigDecimal effectiveRate,
                      Instant expiresAt) {
}