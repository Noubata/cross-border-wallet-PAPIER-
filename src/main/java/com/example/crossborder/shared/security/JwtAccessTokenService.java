package com.example.crossborder.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtAccessTokenService implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final Duration accessTokenTtl;

    public JwtAccessTokenService(JwtEncoder jwtEncoder,
                                 @Value("${security.jwt.access-token-ttl:PT10M}") Duration accessTokenTtl) {
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtl = accessTokenTtl;
    }

    @Override
    public IssuedAccessToken issue(UUID userId, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cross-border-wallet")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(userId.toString())
                .claim("roles", List.of(role))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }
}