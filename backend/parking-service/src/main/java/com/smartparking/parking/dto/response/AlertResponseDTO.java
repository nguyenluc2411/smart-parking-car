package com.smartparking.parking.dto.response;

import com.smartparking.parking.entity.enums.AlertSeverity;
import com.smartparking.parking.entity.enums.AlertStatus;
import com.smartparking.parking.entity.enums.AlertType;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Alert as returned by GET /api/v1/alerts and pushed over SSE. {@code imageUrl} is presigned. */
public record AlertResponseDTO(
        UUID id,
        AlertType alertType,
        AlertSeverity severity,
        String plateNumber,
        String gateId,
        UUID sessionId,
        String imageUrl,
        String message,
        AlertStatus status,
        UUID acknowledgedBy,
        OffsetDateTime acknowledgedAt,
        OffsetDateTime createdAt
) {
}
