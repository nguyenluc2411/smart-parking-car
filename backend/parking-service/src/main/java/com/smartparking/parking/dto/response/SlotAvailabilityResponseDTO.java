package com.smartparking.parking.dto.response;

/** Response for {@code GET /api/v1/slots/availability} (docs/api-contracts.md). */
public record SlotAvailabilityResponseDTO(
        long totalSlots,
        long occupiedSlots,
        long emptySlots,
        long maintenanceSlots,
        double occupancyRate
) {
}
