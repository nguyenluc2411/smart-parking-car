package com.smartparking.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A plate entry as seen by the driver ({@code GET /api/v1/driver/me}). */
public record DriverVehicleResponseDTO(
        UUID id,
        String plateNumber,
        boolean verified,
        OffsetDateTime createdAt
) {
}
