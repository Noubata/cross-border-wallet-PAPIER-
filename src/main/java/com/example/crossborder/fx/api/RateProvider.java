package com.example.crossborder.fx.api;

import java.math.BigDecimal;

public interface RateProvider {
    BigDecimal getNgnToXafMidRate();

    int getSpreadBasisPoints();

    BigDecimal getFlatFee(String sourceCurrency);

    String providerName();
}