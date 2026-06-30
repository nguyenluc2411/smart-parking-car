package com.smartparking.parking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies HS256 access tokens issued by admin-service (same {@code JWT_SECRET}).
 * Verify-only — this service never issues tokens.
 */
@Component
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    /** Verified plate numbers carried in a driver access token (ADR-010). */
    public static final String CLAIM_PLATES = "plates";
    public static final String ROLE_DRIVER = "DRIVER";

    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Parse and verify a token, returning its claims. Throws {@link io.jsonwebtoken.JwtException}. */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
