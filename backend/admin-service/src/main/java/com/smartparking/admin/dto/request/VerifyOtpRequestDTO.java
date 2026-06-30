package com.smartparking.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/driver/auth/verify-otp}. {@code fullName} is required on first verification
 * (registration) and ignored for existing drivers.
 */
public record VerifyOtpRequestDTO(
        @NotBlank
        @Pattern(regexp = "^[0-9+]{8,20}$", message = "Invalid phone number")
        String phone,

        @NotBlank
        @Pattern(regexp = "^[0-9]{4,8}$", message = "Invalid OTP code")
        String code,

        @Size(max = 100)
        String fullName
) {
}
