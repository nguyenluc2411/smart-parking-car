package com.smartparking.parking.dto.response;

import com.smartparking.parking.entity.enums.ReservationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A driver's booking (BR-009). {@code slotCode}/{@code zone} let the app draw the slot on the map. */
public record ReservationResponseDTO(
        UUID id,
        String plateNumber,
        UUID slotId,
        String slotCode,
        String zone,
        Integer gridRow,
        Integer gridCol,
        OffsetDateTime startTime,
        OffsetDateTime holdUntil,
        ReservationStatus status,
        UUID sessionId,
        OffsetDateTime createdAt
) {
}
