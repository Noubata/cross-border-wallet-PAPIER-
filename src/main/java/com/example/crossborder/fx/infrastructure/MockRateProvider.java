package com.example.crossborder.fx.infrastructure;

import com.example.crossborder.fx.api.RateProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MockRateProvider implements RateProvider {

    private final BigDecimal xafPerNgn;
    private final int spreadBasisPoints;
    private final BigDecimal flatFeeNgn;
    private final BigDecimal flatFeeXaf;

    public MockRateProvider(@Value("${fx.mock.xaf-per-ngn:0.42}") BigDecimal xafPerNgn,
                            @Value("${fx.mock.spread-basis-points:150}") int spreadBasisPoints,
                            @Value("${fx.mock.flat-fee-ngn:100.00}") BigDecimal flatFeeNgn,
                            @Value("${fx.mock.flat-fee-xaf:250}") BigDecimal flatFeeXaf) {
        if (xafPerNgn.signum() <= 0 || spreadBasisPoints < 0 || spreadBasisPoints >= 10000
                || flatFeeNgn.signum() < 0 || flatFeeXaf.signum() < 0) {
            throw new IllegalArgumentException("FX mock rate, spread, and fees are outside valid bounds");
        }
        this.xafPerNgn = xafPerNgn;
        this.spreadBasisPoints = spreadBasisPoints;
        this.flatFeeNgn = flatFeeNgn;
        this.flatFeeXaf = flatFeeXaf;
    }

    @Override
    public BigDecimal getNgnToXafMidRate() {
        return xafPerNgn;
    }

    @Override
    public int getSpreadBasisPoints() {
        return spreadBasisPoints;
    }

    @Override
    public BigDecimal getFlatFee(String sourceCurrency) {
        return switch (sourceCurrency) {
            case "NGN" -> flatFeeNgn;
            case "XAF" -> flatFeeXaf;
            default -> throw new IllegalArgumentException("Currency must be NGN or XAF");
        };
    }

    @Override
    public String providerName() {
        return "MOCK_FIXED";
    }
}