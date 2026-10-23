package com.example.crossborder.fx.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateFxQuoteRequest(
        @NotBlank @Pattern(regexp = "NGN|XAF") String sourceCurrency,
        @NotBlank @Pattern(regexp = "NGN|XAF") String destinationCurrency,
        @NotNull @DecimalMin(value = "0.00000001", inclusive = true) BigDecimal sourceAmount
) {
}