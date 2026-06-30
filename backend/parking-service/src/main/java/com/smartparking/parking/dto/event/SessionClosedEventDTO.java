package com.smartparking.parking.dto.event;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

/**
 * Outbound event published to topic {@code parking.session.closed}
 * (consumed by billing-service and admin-service). Shape per docs/api-contracts.md.
 * Kafka key = sessionId.
 */
@Builder(toBuilder = true)
public record SessionClosedEventDTO(
        String eventId,
        UUID sessionId,
        String plateNumber,
        OffsetDateTime entryTime,
        OffsetDateTime exitTime,
        Integer durationSeconds,
        boolean whitelisted   // BR-005-4: whitelist vehicle → billing waives the invoice
) {
}
