package com.smartparking.parking.dto.request;

import com.smartparking.parking.entity.enums.SessionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/sessions/{id}/resolve} — operator reconciles a
 * REQUIRES_ATTENTION session (BR-006-5). {@code status} must be CLOSED or CANCELLED.
 */
public record SessionResolveRequestDTO(
        @NotNull SessionStatus status,
        String note
) {
}
