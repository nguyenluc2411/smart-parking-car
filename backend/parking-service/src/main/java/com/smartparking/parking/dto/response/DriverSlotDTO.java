package com.smartparking.parking.dto.response;

import com.smartparking.parking.entity.enums.SlotStatus;
import java.util.UUID;

/**
 * Response item for {@code GET /api/v1/driver/slots} (BR-009-10).
 *
 * <p>Deliberately trimmed vs {@link SlotResponseDTO}: no {@code currentSessionId} or any other
 * identifier of whoever is parked there. A driver picking a spot on the map needs to know it's
 * free, not who else's plate/session is on the other slots.
 */
public record DriverSlotDTO(
        UUID id,
        String slotCode,
        String zone,
        SlotStatus status,
        Integer gridRow,
        Integer gridCol
) {
}
