package com.example.crossborder.funding.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateFundingRequest(
        @NotNull UUID walletId,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal amount,
        @NotNull @Pattern(regexp = "NGN|XAF") String currency,
        @NotNull @Email @Size(max = 320) String email
) {
}