package com.smartparking.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/login} (docs/api-contracts.md). */
public record LoginRequestDTO(
        @NotBlank String username,
        @NotBlank String password
) {
}
