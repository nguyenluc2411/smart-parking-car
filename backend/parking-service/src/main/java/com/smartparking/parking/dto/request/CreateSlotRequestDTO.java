package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/slots} — create one slot (ADMIN). */
public record CreateSlotRequestDTO(
        @NotBlank @Size(max = 10) String slotCode,
        @NotBlank @Size(max = 5) String zone
) {
}
