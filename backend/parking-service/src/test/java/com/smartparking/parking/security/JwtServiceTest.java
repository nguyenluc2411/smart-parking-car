package com.smartparking.parking.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 32 bytes for HS256
    private final JwtService jwtService = new JwtService(SECRET);

    private String tokenSignedWith(String secret) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("user-1").claim("role", "ADMIN")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void parseClaims_validToken_returnsClaims() {
        Claims claims = jwtService.parseClaims(tokenSignedWith(SECRET));
        assertEquals("user-1", claims.getSubject());
        assertEquals("ADMIN", claims.get(JwtService.CLAIM_ROLE, String.class));
    }

    @Test
    void parseClaims_foreignSecret_throws() {
        String foreign = tokenSignedWith("ffffffffffffffffffffffffffffffff");
        assertThrows(JwtException.class, () -> jwtService.parseClaims(foreign));
    }
}
