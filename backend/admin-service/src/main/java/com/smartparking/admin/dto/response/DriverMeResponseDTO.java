package com.smartparking.admin.dto.response;

import java.util.List;
import java.util.UUID;

/** Response for {@code GET /api/v1/driver/me}. */
public record DriverMeResponseDTO(
        UUID id,
        String phone,
        String fullName,
        List<DriverVehicleResponseDTO> vehicles
) {
}
