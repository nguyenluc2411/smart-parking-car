package com.smartparking.admin.dto.response;

import com.smartparking.admin.entity.enums.Role;
import lombok.Builder;

/** Response for {@code POST /api/v1/auth/login} and {@code /refresh} (docs/api-contracts.md). */
@Builder
public record LoginResponseDTO(
        String accessToken,
        String refreshToken,
        long expiresIn,        // access-token TTL in seconds
        Role role
) {
}
