package com.smartparking.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/driver/me/vehicles}. */
public record AddVehicleRequestDTO(
        @NotBlank
        @Size(max = 20)
        String plateNumber
) {
}
