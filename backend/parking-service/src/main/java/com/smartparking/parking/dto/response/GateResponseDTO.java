package com.smartparking.parking.dto.response;

import com.smartparking.parking.entity.enums.GateDirection;
import com.smartparking.parking.entity.enums.GateStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response for {@code GET /api/v1/gates} and {@code POST /api/v1/gates/{id}/override}. */
public record GateResponseDTO(
        UUID id,
        String gateCode,
        GateDirection direction,
        GateStatus status,
        boolean hasBarrier,
        UUID parkingLotId,
        UUID floorId,
        String lastCommand,
        OffsetDateTime lastCommandAt
) {
}
