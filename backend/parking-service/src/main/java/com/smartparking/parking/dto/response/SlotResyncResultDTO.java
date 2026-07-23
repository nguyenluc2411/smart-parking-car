package com.smartparking.parking.dto.response;

/** Result of {@code POST /api/v1/slots/resync} (BR-003-4). */
public record SlotResyncResultDTO(
        long totalSlots,
        long occupiedSlots,
        long reservedSlots,
        long emptySlots,
        long maintenanceSlots,
        int correctedSlots
) {
}
