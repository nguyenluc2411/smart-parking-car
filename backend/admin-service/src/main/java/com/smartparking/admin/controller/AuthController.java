package com.smartparking.admin.controller;

import com.smartparking.admin.dto.request.LoginRequestDTO;
import com.smartparking.admin.dto.request.LogoutRequestDTO;
import com.smartparking.admin.dto.request.RefreshRequestDTO;
import com.smartparking.admin.dto.response.ApiResponse;
import com.smartparking.admin.dto.response.LoginResponseDTO;
import com.smartparking.admin.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints (docs/api-contracts.md). Public — secured by the credentials/tokens they
 * carry. Controller only — no business logic (CLAUDE.md §6.4).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutRequestDTO request) {
        // Body is optional: the dashboard logs out client-side; the refresh token (if sent) is revoked.
        authService.logout(request);
        return ApiResponse.ok(null);
    }
}
