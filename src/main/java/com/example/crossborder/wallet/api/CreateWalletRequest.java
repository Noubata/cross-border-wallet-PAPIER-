package com.example.crossborder.wallet.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

public record CreateWalletRequest(@NotBlank @Pattern(regexp = "NGN|XAF") String currency) {
}