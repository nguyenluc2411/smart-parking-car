package com.smartparking.parking.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Inbound event consumed from topic {@code parking.plate.detected} (produced by edge-agent).
 * Shape per docs/api-contracts.md.
 *
 * <p>Modelled as an immutable record; unknown fields are ignored for forward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlateDetectedEventDTO(
        String eventId,
        String plateNumber,
        BigDecimal confidence,
        String gateId,        // gate_code, e.g. GATE_ENTRY_01
        String direction,     // IN | OUT
        OffsetDateTime timestamp,
        String imageRef,
        Integer processingMs
) {
}
