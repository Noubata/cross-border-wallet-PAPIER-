package com.example.crossborder.funding.api;

import com.example.crossborder.funding.application.FundingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/funding")
public class FundingController {

    private final FundingService fundingService;

    public FundingController(FundingService fundingService) {
        this.fundingService = fundingService;
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public FundingPaymentView createPayment(Authentication authentication,
                                            @Valid @RequestBody CreateFundingRequest request) {
        return fundingService.createPayment(UUID.fromString(authentication.getName()), request);
    }

    @PostMapping("/webhooks/paystack")
    public WebhookResult webhook(@RequestHeader("x-paystack-signature") String signature,
                                 @RequestBody byte[] rawBody) {
        return fundingService.handleWebhook(rawBody, signature);
    }
}