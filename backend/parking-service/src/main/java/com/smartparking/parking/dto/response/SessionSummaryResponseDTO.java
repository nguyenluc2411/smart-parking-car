package com.smartparking.parking.dto.response;

import com.smartparking.parking.entity.enums.SessionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A row in {@code GET /api/v1/sessions} (docs/api-contracts.md). */
public record SessionSummaryResponseDTO(
        UUID id,
        String plateNumber,
        String slotCode,
        OffsetDateTime entryTime,
        OffsetDateTime exitTime,
        Integer durationSeconds,
        SessionStatus status
) {
}
