package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * Request body for {@code POST /api/v1/driver/reservations} (BR-009).
 *
 * <p>The driver picks a plate and an arrival time; the slot is chosen by the server. Letting the
 * client name a slot would leak the lot's layout and invite scripted grabbing of the good spots.
 */
public record CreateReservationRequestDTO(
        @NotBlank String plateNumber,
        /** When the driver expects to arrive; the hold runs from here (BR-009-2). */
        @NotNull OffsetDateTime startTime
) {
}
