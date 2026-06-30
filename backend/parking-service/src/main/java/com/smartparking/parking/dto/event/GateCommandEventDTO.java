package com.smartparking.parking.dto.event;

import com.smartparking.parking.entity.enums.GateCommand;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

/**
 * Outbound event published to topic {@code parking.gate.command} (consumed by edge-agent).
 * Shape per docs/api-contracts.md. Kafka key = gateId.
 */
@Builder
public record GateCommandEventDTO(
        String eventId,
        String gateId,        // gate_code
        GateCommand command,
        UUID sessionId,
        OffsetDateTime triggeredAt
) {
}
