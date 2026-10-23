package com.example.crossborder.transfer.api;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(@NotNull UUID sourceWalletId,
                                    @NotNull UUID destinationWalletId,
                                    @NotNull BigDecimal amount) {
}