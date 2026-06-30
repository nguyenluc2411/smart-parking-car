package com.smartparking.admin.controller;

import com.smartparking.admin.dto.request.RefreshRequestDTO;
import com.smartparking.admin.dto.request.RequestOtpRequestDTO;
import com.smartparking.admin.dto.request.LogoutRequestDTO;
import com.smartparking.admin.dto.request.VerifyOtpRequestDTO;
import com.smartparking.admin.dto.response.ApiResponse;
import com.smartparking.admin.dto.response.DriverLoginResponseDTO;
import com.smartparking.admin.dto.response.OtpChallengeResponseDTO;
import com.smartparking.admin.service.DriverAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driver authentication (phone + OTP). Public — secured by the OTP/tokens it carries (ADR-010).
 * Controller only (CLAUDE.md §6.4).
 */
@RestController
@RequestMapping("/api/v1/driver/auth")
@RequiredArgsConstructor
public class DriverAuthController {

    private final DriverAuthService driverAuthService;

    @PostMapping("/request-otp")
    public ApiResponse<OtpChallengeResponseDTO> requestOtp(
            @Valid @RequestBody RequestOtpRequestDTO request) {
        return ApiResponse.ok(driverAuthService.requestOtp(request));
    }

    @PostMapping("/verify-otp")
    public ApiResponse<DriverLoginResponseDTO> verifyOtp(
            @Valid @RequestBody VerifyOtpRequestDTO request) {
        return ApiResponse.ok(driverAuthService.verifyOtp(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<DriverLoginResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO request) {
        return ApiResponse.ok(driverAuthService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutRequestDTO request) {
        driverAuthService.logout(request == null ? null : request.refreshToken());
        return ApiResponse.ok(null);
    }
}
