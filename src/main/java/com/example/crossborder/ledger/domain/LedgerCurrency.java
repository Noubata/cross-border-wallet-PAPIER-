package com.example.crossborder.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum LedgerCurrency {
    NGN(2),
    XAF(0);

    private final int fractionDigits;

    LedgerCurrency(int fractionDigits) {
        this.fractionDigits = fractionDigits;
    }

    public long toMinorUnits(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Ledger amounts must be positive");
        }
        try {
            return amount.setScale(fractionDigits, RoundingMode.UNNECESSARY)
                    .movePointRight(fractionDigits).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Amount has unsupported precision or is out of range", exception);
        }
    }

    public BigDecimal fromMinorUnits(long minorUnits) {
        return BigDecimal.valueOf(minorUnits, fractionDigits);
    }

    public static LedgerCurrency fromCode(String code) {
        try {
            return LedgerCurrency.valueOf(code);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Currency must be NGN or XAF", exception);
        }
    }
}