package com.smartparking.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/refresh}. */
public record RefreshRequestDTO(
        @NotBlank String refreshToken
) {
}
