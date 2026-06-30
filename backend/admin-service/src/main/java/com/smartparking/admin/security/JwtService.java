package com.smartparking.admin.security;

import com.smartparking.admin.entity.Driver;
import com.smartparking.admin.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and parses HS256 access tokens. admin-service is the sole JWT issuer; other services
 * validate with the same {@code JWT_SECRET}.
 */
@Component
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_USERNAME = "username";
    /** Verified plate numbers carried in a driver access token (ADR-010). */
    public static final String CLAIM_PLATES = "plates";
    public static final String ROLE_DRIVER = "DRIVER";

    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-seconds}") long accessTtlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
    }

    /** Build a signed access token with the user id as subject and role/username claims. */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * Build a signed access token for a driver: subject = driver id, role = {@code DRIVER}, and the
     * {@code plates} claim carrying the driver's verified plate numbers (used by parking/billing to
     * scope "my" data without a cross-service DB call).
     */
    public String generateDriverAccessToken(Driver driver, List<String> verifiedPlates) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(driver.getId().toString())
                .claim(CLAIM_USERNAME, driver.getPhone())
                .claim(CLAIM_ROLE, ROLE_DRIVER)
                .claim(CLAIM_PLATES, verifiedPlates)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    /** Parse and verify a token, returning its claims. Throws {@link io.jsonwebtoken.JwtException}. */
    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }
}
