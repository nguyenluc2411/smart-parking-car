package com.smartparking.parking.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

/**
 * Inbound event consumed from topic {@code parking.gate.state} (produced by edge-agent).
 * Shape per docs/api-contracts.md.
 *
 * <p>Reflects the physical barrier state so {@code gates.status} stays in sync — in particular
 * the auto-close after {@code GATE_AUTO_CLOSE_SECONDS} (BR-006-2), which has no other signal back
 * to parking-service. Unknown fields are ignored for forward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GateStateEventDTO(
        String eventId,
        String gateId,        // gate_code, e.g. GATE_ENTRY_01
        String status,        // OPEN | CLOSED
        String reason,        // command | auto | startup
        OffsetDateTime timestamp
) {
}
