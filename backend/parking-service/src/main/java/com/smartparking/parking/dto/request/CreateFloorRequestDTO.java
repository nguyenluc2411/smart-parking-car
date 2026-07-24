package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CreateFloorRequestDTO(
        @NotBlank @Size(max = 20) String floorCode,
        @NotBlank @Size(max = 80) String name,
        @Min(1) @Max(8) Integer zoneCount,
        @Min(0) @Max(100) Integer slotsPerZone
) {
}
