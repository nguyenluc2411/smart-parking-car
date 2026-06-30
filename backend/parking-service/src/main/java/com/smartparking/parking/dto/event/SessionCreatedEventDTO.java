package com.smartparking.parking.dto.event;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

/**
 * Outbound event published to topic {@code parking.session.created}
 * (consumed by billing-service and admin-service). Shape per docs/api-contracts.md.
 * Kafka key = sessionId.
 */
@Builder(toBuilder = true)
public record SessionCreatedEventDTO(
        String eventId,
        UUID sessionId,
        String plateNumber,
        UUID slotId,
        String slotCode,
        OffsetDateTime entryTime,
        String gateId         // gate_code
) {
}
