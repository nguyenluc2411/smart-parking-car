package com.smartparking.admin.dto.response;

import lombok.Builder;

/**
 * Response for {@code POST /api/v1/driver/auth/verify-otp} and {@code /refresh}. {@code role} is the
 * literal {@code "DRIVER"} (drivers are not in the {@link com.smartparking.admin.entity.enums.Role}
 * enum, which is operator/admin only).
 */
@Builder
public record DriverLoginResponseDTO(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String role
) {
}
