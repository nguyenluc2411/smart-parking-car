package com.smartparking.billing.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Inbound event consumed from topic {@code parking.session.closed} (produced by parking-service).
 * Shape per docs/api-contracts.md. Each service owns its copy of the DTO (no cross-service code).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionClosedEventDTO(
        String eventId,
        UUID sessionId,
        String plateNumber,
        OffsetDateTime entryTime,
        OffsetDateTime exitTime,
        Integer durationSeconds,
        boolean whitelisted   // BR-005-4: whitelist vehicle → invoice is WAIVED (free)
) {
}
