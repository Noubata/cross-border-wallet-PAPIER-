package com.example.crossborder.shared.security;

import java.util.UUID;

public interface AccessTokenIssuer {
    IssuedAccessToken issue(UUID userId, String role);
}