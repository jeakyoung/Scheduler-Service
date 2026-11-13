package com.scheduler.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Stateless JWT issuing/parsing for admin login (PLAN.md §6, auth decided 2026-11-13: token-
 * based, not session cookies - a React SPA on a different origin would otherwise need
 * SameSite=None+Secure+CORS-credentials+CSRF defenses, unnecessary complexity for a single-
 * admin-role internal tool). Signing key is separate from CredentialCipher's master key -
 * signing and encryption keys shouldn't be shared.
 */
@Component
public class JwtTokenService {

    private static final String ROLE_CLAIM = "role";
    private static final int REQUIRED_KEY_LENGTH_BYTES = 32;

    private final SecretKey key;
    private final Duration expiration;

    public JwtTokenService(
            @Value("${scheduler-service.jwt.signing-key}") String base64Key,
            @Value("${scheduler-service.jwt.expiration-minutes}") long expirationMinutes
    ) {
        this.key = loadKey(base64Key);
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String generate(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public String parseUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String parseRole(String token) {
        return parseClaims(token).get(ROLE_CLAIM, String.class);
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidJwtException("Invalid or expired token", e);
        }
    }

    private static SecretKey loadKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY is not set. Generate one with e.g. `openssl rand -base64 32` " +
                            "and set it as an environment variable before starting the application.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SIGNING_KEY must be valid base64", e);
        }

        if (keyBytes.length != REQUIRED_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY must decode to exactly " + REQUIRED_KEY_LENGTH_BYTES +
                            " bytes (HS256), but decoded to " + keyBytes.length + " bytes");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }
}
