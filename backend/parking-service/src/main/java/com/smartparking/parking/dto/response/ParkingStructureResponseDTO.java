package com.smartparking.parking.dto.response;

import java.util.List;
import java.util.UUID;

public record ParkingStructureResponseDTO(
        ParkingLotResponseDTO lot,
        List<Floor> floors
) {
    public record Floor(UUID id, String floorCode, String name, int sortOrder, List<Zone> zones) {}
    public record Zone(UUID id, String zoneCode, String name, long slotCount) {}
}
