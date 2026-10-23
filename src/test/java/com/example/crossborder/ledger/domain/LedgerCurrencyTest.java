package com.example.crossborder.ledger.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerCurrencyTest {

    @Test
    void convertsNgnToKoboWithoutRounding() {
        assertEquals(12345L, LedgerCurrency.NGN.toMinorUnits(new BigDecimal("123.45")));
        assertEquals(new BigDecimal("123.45"), LedgerCurrency.NGN.fromMinorUnits(12345L));
    }

    @Test
    void convertsXafToWholeMinorUnits() {
        assertEquals(123L, LedgerCurrency.XAF.toMinorUnits(new BigDecimal("123")));
        assertEquals(new BigDecimal("123"), LedgerCurrency.XAF.fromMinorUnits(123L));
    }

    @Test
    void rejectsRoundingAndNonPositiveValues() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerCurrency.NGN.toMinorUnits(new BigDecimal("1.001")));
        assertThrows(IllegalArgumentException.class,
                () -> LedgerCurrency.XAF.toMinorUnits(new BigDecimal("1.1")));
        assertThrows(IllegalArgumentException.class,
                () -> LedgerCurrency.NGN.toMinorUnits(BigDecimal.ZERO));
    }
}