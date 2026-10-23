package com.example.crossborder.customer.api;

import com.example.crossborder.customer.application.CustomerProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers/me")
public class CustomerController {

    private final CustomerProfileService customerProfileService;

    public CustomerController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @GetMapping
    public CustomerProfileView getProfile(Authentication authentication) {
        return customerProfileService.getProfile(UUID.fromString(authentication.getName()));
    }

    @PatchMapping("/language")
    public CustomerProfileView updatePreferredLanguage(Authentication authentication,
                                                       @Valid @RequestBody PreferredLanguageRequest request) {
        return customerProfileService.updatePreferredLanguage(
                UUID.fromString(authentication.getName()), request.preferredLanguage());
    }
}