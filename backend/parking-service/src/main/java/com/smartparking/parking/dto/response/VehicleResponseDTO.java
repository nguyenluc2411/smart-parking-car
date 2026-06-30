package com.smartparking.parking.dto.response;

import com.smartparking.parking.entity.enums.VehicleType;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response for {@code GET/POST /api/v1/vehicles/whitelist}. */
public record VehicleResponseDTO(
        UUID id,
        String plateNumber,
        VehicleType vehicleType,
        String ownerName,
        String note,
        OffsetDateTime createdAt,
        /** Set only on a POST that moved a plate between lists (e.g. WHITELIST); null otherwise. */
        VehicleType reclassifiedFrom
) {
}
