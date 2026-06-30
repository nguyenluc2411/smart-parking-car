package com.smartparking.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/logout}. */
public record LogoutRequestDTO(
        @NotBlank String refreshToken
) {
}
