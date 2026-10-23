package com.example.crossborder.customer.api;

import java.util.UUID;

public record CustomerProfileView(UUID customerId, String preferredLanguage, String kycStatus) {
}