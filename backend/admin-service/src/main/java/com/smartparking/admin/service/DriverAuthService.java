package com.smartparking.admin.service;

import com.smartparking.admin.dto.request.RequestOtpRequestDTO;
import com.smartparking.admin.dto.request.VerifyOtpRequestDTO;
import com.smartparking.admin.dto.response.DriverLoginResponseDTO;
import com.smartparking.admin.dto.response.OtpChallengeResponseDTO;

/**
 * Driver authentication via phone + OTP (ADR-010). Contract only — no business logic / no
 * {@code @Transactional} here (CLAUDE.md §6.4).
 */
public interface DriverAuthService {

    OtpChallengeResponseDTO requestOtp(RequestOtpRequestDTO request);

    /** Verify the OTP; auto-registers a new driver when the phone is unknown. */
    DriverLoginResponseDTO verifyOtp(VerifyOtpRequestDTO request);

    DriverLoginResponseDTO refresh(String refreshToken);

    void logout(String refreshToken);
}
