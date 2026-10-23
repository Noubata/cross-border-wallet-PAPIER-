package com.example.crossborder.wallet.domain;

import java.math.BigDecimal;

public enum WalletCurrency {
    NGN(2),
    XAF(0);

    private final int fractionDigits;

    WalletCurrency(int fractionDigits) {
        this.fractionDigits = fractionDigits;
    }

    public BigDecimal fromMinorUnits(long minorUnits) {
        return BigDecimal.valueOf(minorUnits, fractionDigits);
    }

    public static WalletCurrency fromCode(String code) {
        try {
            return WalletCurrency.valueOf(code);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Currency must be NGN or XAF", exception);
        }
    }
}