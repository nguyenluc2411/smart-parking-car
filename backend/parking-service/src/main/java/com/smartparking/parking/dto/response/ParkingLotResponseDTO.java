package com.smartparking.parking.dto.response;

import java.util.UUID;

public record ParkingLotResponseDTO(
        UUID id,
        String lotCode,
        String name,
        String address,
        boolean active
) {
}
