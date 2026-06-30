package com.smartparking.admin.service;

import com.smartparking.admin.dto.request.LoginRequestDTO;
import com.smartparking.admin.dto.request.LogoutRequestDTO;
import com.smartparking.admin.dto.request.RefreshRequestDTO;
import com.smartparking.admin.dto.response.LoginResponseDTO;

/**
 * Authentication use cases. admin-service is the JWT issuer.
 *
 * <p>Contract only — no business logic, no {@code @Transactional} here (CLAUDE.md §6.4).
 */
public interface AuthService {

    /**
     * Validate credentials, issue an access token + a refresh token, audit the login (BR-007-3).
     *
     * @throws com.smartparking.admin.exception.InvalidCredentialsException on any auth failure
     */
    LoginResponseDTO login(LoginRequestDTO request);

    /**
     * Issue a new access token from a valid (non-revoked, non-expired) refresh token.
     *
     * @throws com.smartparking.admin.exception.InvalidTokenException if the refresh token is invalid
     */
    LoginResponseDTO refresh(RefreshRequestDTO request);

    /** Revoke a refresh token and audit the logout (BR-007-3). Idempotent. */
    void logout(LogoutRequestDTO request);
}
