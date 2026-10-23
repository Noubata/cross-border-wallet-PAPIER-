package com.example.crossborder.customer.api;

import java.util.UUID;

public interface CustomerRegistrationService {
    void createPendingProfile(UUID customerId, String preferredLanguage);
}