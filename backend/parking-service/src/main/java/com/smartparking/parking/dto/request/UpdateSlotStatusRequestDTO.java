package com.smartparking.parking.dto.request;

import com.smartparking.parking.entity.enums.SlotStatus;
import jakarta.validation.constraints.NotNull;

/** Request body for {@code PATCH /api/v1/slots/{id}/status} — set EMPTY or MAINTENANCE (ADMIN). */
public record UpdateSlotStatusRequestDTO(
        @NotNull SlotStatus status
) {
}
