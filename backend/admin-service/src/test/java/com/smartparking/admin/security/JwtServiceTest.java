package com.smartparking.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartparking.admin.entity.User;
import com.smartparking.admin.entity.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 32 bytes for HS256
    private final JwtService jwtService = new JwtService(SECRET, 3600);

    private User user() {
        return User.builder()
                .id(UUID.randomUUID()).username("admin").role(Role.ADMIN).active(true).build();
    }

    @Test
    void generateThenParse_roundTripsClaims() {
        User user = user();
        String token = jwtService.generateAccessToken(user);

        Claims claims = jwtService.parseClaims(token);
        assertEquals(user.getId().toString(), claims.getSubject());
        assertEquals("admin", claims.get(JwtService.CLAIM_USERNAME, String.class));
        assertEquals("ADMIN", claims.get(JwtService.CLAIM_ROLE, String.class));
    }

    @Test
    void parse_tokenSignedWithDifferentSecret_throws() {
        String foreignToken = new JwtService("ffffffffffffffffffffffffffffffff", 3600)
                .generateAccessToken(user());

        assertThrows(JwtException.class, () -> jwtService.parseClaims(foreignToken));
    }
}
