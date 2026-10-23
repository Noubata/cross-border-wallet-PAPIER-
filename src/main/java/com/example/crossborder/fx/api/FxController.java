package com.example.crossborder.fx.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/fx")
public class FxController {

    private final FxQuoteService fxQuoteService;

    public FxController(FxQuoteService fxQuoteService) {
        this.fxQuoteService = fxQuoteService;
    }

    @PostMapping("/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    public FxQuote createQuote(Authentication authentication, @Valid @RequestBody CreateFxQuoteRequest request) {
        return fxQuoteService.createQuote(UUID.fromString(authentication.getName()), request.sourceCurrency(),
                request.destinationCurrency(), request.sourceAmount());
    }
}