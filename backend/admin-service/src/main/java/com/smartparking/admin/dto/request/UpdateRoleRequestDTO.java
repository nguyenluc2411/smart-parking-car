package com.smartparking.admin.dto.request;

import com.smartparking.admin.entity.enums.Role;
import jakarta.validation.constraints.NotNull;

/** Request body for {@code PUT /api/v1/users/{id}/role} (ADMIN). */
public record UpdateRoleRequestDTO(
        @NotNull Role role
) {
}
