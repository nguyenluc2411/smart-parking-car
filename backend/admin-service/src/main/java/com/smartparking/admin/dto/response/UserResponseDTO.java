package com.smartparking.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartparking.admin.entity.enums.Role;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response for {@code GET/POST /api/v1/users}. */
public record UserResponseDTO(
        UUID id,
        String username,
        String email,
        Role role,
        @JsonProperty("isActive") boolean isActive,
        OffsetDateTime createdAt
) {
}
