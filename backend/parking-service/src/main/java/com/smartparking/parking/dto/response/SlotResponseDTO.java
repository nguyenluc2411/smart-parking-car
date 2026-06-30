package com.smartparking.parking.dto.response;

import com.smartparking.parking.entity.enums.SlotStatus;
import java.util.UUID;

/** Response item for {@code GET /api/v1/slots}. */
public record SlotResponseDTO(
        UUID id,
        String slotCode,
        String zone,
        SlotStatus status,
        UUID currentSessionId
) {
}
