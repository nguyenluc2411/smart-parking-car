package com.smartparking.parking.dto.response;

import com.smartparking.parking.entity.enums.SlotStatus;
import java.util.UUID;

/**
 * Response item for {@code GET /api/v1/slots}.
 *
 * <p>{@code gridRow}/{@code gridCol} place the slot on the zone map (BR-003-6). Both are null for
 * slots created before the map existed — clients fall back to a plain list rather than guessing a
 * position, because a wrong position on a map is worse than no map.
 */
public record SlotResponseDTO(
        UUID id,
        String slotCode,
        String zone,
        UUID zoneId,
        SlotStatus status,
        UUID currentSessionId,
        Integer gridRow,
        Integer gridCol
) {
}
