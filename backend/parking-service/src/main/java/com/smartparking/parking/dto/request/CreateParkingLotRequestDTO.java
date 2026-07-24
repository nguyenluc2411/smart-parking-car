package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateParkingLotRequestDTO(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]+") @Size(max = 10) String lotCode,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 255) String address,
        boolean createTemplate,
        @Min(1) @Max(8) Integer groundZoneCount,
        @Min(1) @Max(100) Integer slotsPerZone
) {
}
