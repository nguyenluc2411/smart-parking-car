package com.smartparking.admin.service.impl;

import com.smartparking.admin.dto.request.LoginRequestDTO;
import com.smartparking.admin.dto.request.LogoutRequestDTO;
import com.smartparking.admin.dto.request.RefreshRequestDTO;
import com.smartparking.admin.dto.response.LoginResponseDTO;
import com.smartparking.admin.entity.RefreshToken;
import com.smartparking.admin.entity.User;
import com.smartparking.admin.exception.InvalidCredentialsException;
import com.smartparking.admin.exception.InvalidTokenException;
import com.smartparking.admin.repository.RefreshTokenRepository;
import com.smartparking.admin.repository.UserRepository;
import com.smartparking.admin.security.JwtService;
import com.smartparking.admin.service.AuditService;
import com.smartparking.admin.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login / refresh / logout. Access tokens are stateless JWTs; refresh tokens are opaque random
 * strings persisted as SHA-256 hashes (so the raw token is never stored). Login and logout are
 * audited (BR-007-3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    @Value("${app.jwt.refresh-token-ttl-seconds}")
    private long refreshTtlSeconds;

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByUsername(request.username())
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = issueRefreshToken(user);

        auditService.recordUserAction(user.getId(), "USER_LOGIN", "User", user.getId().toString(), null);
        log.info("Login success: username={}, role={}", user.getUsername(), user.getRole());

        return LoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessTtlSeconds())
                .role(user.getRole())
                .build();
    }

    @Override
    @Transactional
    public LoginResponseDTO refresh(RefreshRequestDTO request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(request.refreshToken()))
                .orElseThrow(() -> new InvalidTokenException("Refresh token not recognised"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException("Refresh token is revoked or expired");
        }

        User user = userRepository.findById(stored.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidTokenException("User no longer active"));

        return LoginResponseDTO.builder()
                .accessToken(jwtService.generateAccessToken(user))
                .refreshToken(request.refreshToken())   // same refresh token (no rotation)
                .expiresIn(jwtService.getAccessTtlSeconds())
                .role(user.getRole())
                .build();
    }

    @Override
    @Transactional
    public void logout(LogoutRequestDTO request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            return; // nothing to revoke (client-side logout)
        }
        refreshTokenRepository.findByTokenHash(sha256(request.refreshToken())).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            auditService.recordUserAction(token.getUserId(), "USER_LOGOUT", "User",
                    token.getUserId().toString(), null);
            log.info("Logout: refresh token revoked for userId={}", token.getUserId());
        });
    }

    private String issueRefreshToken(User user) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(rawToken))
                .expiresAt(OffsetDateTime.now().plusSeconds(refreshTtlSeconds))
                .revoked(false)
                .build());
        return rawToken;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
