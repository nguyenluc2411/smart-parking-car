package com.smartparking.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/driver/reservations/{id}/fee} (BR-009-11).
 *
 * <p>billing-service does not have its own copy of the reservation (Database Per Service), so the
 * client supplies the fields needed to compute and later refund the fee. Trusted because it's
 * scoped to the same DRIVER JWT parking-service already validated when creating the reservation.
 */
public record CreateReservationFeeRequestDTO(
        @NotBlank String plateNumber,
        @NotNull OffsetDateTime reservationStartTime
) {
}
