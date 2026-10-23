package com.example.crossborder.customer.api;

import java.util.UUID;

public interface CustomerKycQuery {
    String getKycStatus(UUID customerId);
}