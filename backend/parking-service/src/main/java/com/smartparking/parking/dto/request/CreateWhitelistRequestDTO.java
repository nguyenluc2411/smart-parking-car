package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/vehicles/whitelist} (docs/api-contracts.md). */
public record CreateWhitelistRequestDTO(
        @NotBlank(message = "Vui lòng nhập biển số") String plateNumber,
        String ownerName,
        String note,
        /** Confirm re-classifying a plate already in the OTHER list (whitelist<->blacklist). */
        boolean force
) {
}
