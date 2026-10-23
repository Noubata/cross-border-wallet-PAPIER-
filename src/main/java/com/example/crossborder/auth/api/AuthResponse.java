package com.example.crossborder.auth.api;

import java.util.UUID;

public record AuthResponse(UUID userId, String email, String role, String accessToken,
                           String refreshToken, long accessTokenExpiresInSeconds) {
}