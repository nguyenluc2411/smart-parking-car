package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/driver/reservations} (BR-009).
 *
 * <p>The driver picks a plate and an arrival time. {@code slotId} is optional (BR-009-10): pick it
 * off {@code GET /api/v1/driver/slots} to claim that exact spot, or leave it null to let the
 * server assign the first EMPTY slot as before.
 */
public record CreateReservationRequestDTO(
        @NotBlank String plateNumber,
        /** When the driver expects to arrive; the hold runs from here (BR-009-2). */
        @NotNull OffsetDateTime startTime,
        /** BR-009-10: driver-chosen slot from the map. Null -> server auto-assigns. */
        UUID slotId
) {
}
