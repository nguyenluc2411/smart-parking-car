package com.smartparking.parking.dto.response;

/**
 * Response for {@code GET /api/v1/slots/availability} (docs/api-contracts.md).
 *
 * <p>{@code occupancyRate} counts {@code reservedSlots} as used, because that is what the entry
 * flow does (BR-009-4): a held slot cannot take a walk-in, so a gauge that ignored it would read
 * "half empty" on a lot that is turning cars away.
 */
public record SlotAvailabilityResponseDTO(
        long totalSlots,
        long occupiedSlots,
        long reservedSlots,
        long emptySlots,
        long maintenanceSlots,
        double occupancyRate
) {
}
