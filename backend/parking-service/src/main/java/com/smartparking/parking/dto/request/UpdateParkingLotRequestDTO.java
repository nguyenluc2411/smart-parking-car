package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateParkingLotRequestDTO(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 255) String address
) {
}
