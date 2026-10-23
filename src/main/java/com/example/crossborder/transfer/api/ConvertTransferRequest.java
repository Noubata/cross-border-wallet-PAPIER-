package com.example.crossborder.transfer.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConvertTransferRequest(@NotNull UUID sourceWalletId,
                                     @NotNull UUID destinationWalletId,
                                     @NotNull UUID quoteId) {
}