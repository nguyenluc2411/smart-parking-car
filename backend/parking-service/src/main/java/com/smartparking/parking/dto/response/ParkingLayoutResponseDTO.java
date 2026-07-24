package com.smartparking.parking.dto.response;

import com.smartparking.parking.dto.request.SaveParkingLayoutRequestDTO;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ParkingLayoutResponseDTO(
        UUID parkingLotId,
        UUID floorId,
        int canvasWidth,
        int canvasHeight,
        int draftVersion,
        int publishedVersion,
        OffsetDateTime publishedAt,
        List<SaveParkingLayoutRequestDTO.Element> elements
) {
}
