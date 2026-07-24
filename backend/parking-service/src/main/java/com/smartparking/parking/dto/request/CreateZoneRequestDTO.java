package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateZoneRequestDTO(
        @NotBlank @Size(max = 10) String zoneCode,
        @NotBlank @Size(max = 80) String name,
        @Min(0) @Max(500) int initialSlots
) {
}
