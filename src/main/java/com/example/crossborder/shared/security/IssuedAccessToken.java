package com.example.crossborder.shared.security;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {
}