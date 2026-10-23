package com.example.crossborder.ledger.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class LedgerCurrencyRules {

    private LedgerCurrencyRules() {
    }

    public static long toMinorUnits(BigDecimal amount, String currencyCode) {
        int fractionDigits = fractionDigits(currencyCode);
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        try {
            return amount.setScale(fractionDigits, RoundingMode.UNNECESSARY)
                    .movePointRight(fractionDigits).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Amount has unsupported precision or is out of range", exception);
        }
    }

    public static BigDecimal fromMinorUnits(long amount, String currencyCode) {
        return BigDecimal.valueOf(amount, fractionDigits(currencyCode));
    }

    private static int fractionDigits(String currencyCode) {
        return switch (currencyCode) {
            case "NGN" -> 2;
            case "XAF" -> 0;
            default -> throw new IllegalArgumentException("Currency must be NGN or XAF");
        };
    }
}