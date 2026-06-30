package com.smartparking.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Response item for {@code GET /api/v1/audit-logs}. {@code payload} is the raw JSON object. */
public record AuditLogResponseDTO(
        UUID id,
        UUID userId,
        String username,
        String action,
        String targetEntity,
        String targetId,
        Object payload,
        String sourceService,
        OffsetDateTime createdAt
) {
}
