package com.smartparking.admin.dto.response;

/** Response for {@code POST /api/v1/driver/auth/request-otp} (does not reveal registration state). */
public record OtpChallengeResponseDTO(
        String channel,        // SMS
        long expiresIn,        // seconds until the OTP expires
        long resendAfter       // seconds before a new OTP may be requested
) {
}
