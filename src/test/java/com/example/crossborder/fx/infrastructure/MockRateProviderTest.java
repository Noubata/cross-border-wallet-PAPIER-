package com.example.crossborder.fx.infrastructure;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockRateProviderTest {

    @Test
    void exposesConfiguredRateSpreadFeesAndName() {
        MockRateProvider provider = new MockRateProvider(new BigDecimal("0.42"), 150,
                new BigDecimal("100.00"), new BigDecimal("250"));

        assertEquals(new BigDecimal("0.42"), provider.getNgnToXafMidRate());
        assertEquals(150, provider.getSpreadBasisPoints());
        assertEquals(new BigDecimal("100.00"), provider.getFlatFee("NGN"));
        assertEquals(new BigDecimal("250"), provider.getFlatFee("XAF"));
        assertEquals("MOCK_FIXED", provider.providerName());
    }

    @Test
    void rejectsInvalidProviderConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new MockRateProvider(BigDecimal.ZERO, 150, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new MockRateProvider(BigDecimal.ONE, 10000, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new MockRateProvider(BigDecimal.ONE, 0, new BigDecimal("-1"), BigDecimal.ZERO));
    }

    @Test
    void rejectsUnsupportedFeeCurrency() {
        MockRateProvider provider = new MockRateProvider(BigDecimal.ONE, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> provider.getFlatFee("USD"));
    }
}