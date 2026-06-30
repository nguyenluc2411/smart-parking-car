package com.smartparking.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** {@code POST /api/v1/driver/auth/request-otp}. */
public record RequestOtpRequestDTO(
        @NotBlank
        @Pattern(regexp = "^[0-9+]{8,20}$", message = "Invalid phone number")
        String phone
) {
}
