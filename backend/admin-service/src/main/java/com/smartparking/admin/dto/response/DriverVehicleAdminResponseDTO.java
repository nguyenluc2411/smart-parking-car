package com.smartparking.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A plate claim as seen by operator/admin during verification ({@code GET /api/v1/driver-vehicles}). */
public record DriverVehicleAdminResponseDTO(
        UUID id,
        UUID driverId,
        String driverPhone,
        String driverName,
        String plateNumber,
        boolean verified,
        OffsetDateTime createdAt
) {
}
