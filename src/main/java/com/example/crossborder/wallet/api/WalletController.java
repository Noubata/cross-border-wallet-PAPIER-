package com.example.crossborder.wallet.api;

import com.example.crossborder.wallet.application.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletView createWallet(Authentication authentication,
                                   @Valid @RequestBody CreateWalletRequest request) {
        return walletService.createWallet(UUID.fromString(authentication.getName()), request.currency());
    }

    @GetMapping
    public List<WalletView> getWallets(Authentication authentication) {
        return walletService.getWallets(UUID.fromString(authentication.getName()));
    }

    @GetMapping("/{currency}")
    public WalletView getWallet(Authentication authentication, @PathVariable String currency) {
        return walletService.getWallet(UUID.fromString(authentication.getName()), currency);
    }
}