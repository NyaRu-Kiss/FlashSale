package com.flashsale.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public final class JwtTokenService {
    private final SecretKey key;
    private final Duration ttl;

    public JwtTokenService(String secret, Duration ttl) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    public String issue(Principal principal) {
        Instant now = Instant.now();
        return Jwts.builder().subject(Long.toString(principal.userId()))
                .claim("role", principal.role().name()).issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl))).signWith(key).compact();
    }

    public Principal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new Principal(Long.parseLong(claims.getSubject()), Role.valueOf(claims.get("role", String.class)));
    }
}
