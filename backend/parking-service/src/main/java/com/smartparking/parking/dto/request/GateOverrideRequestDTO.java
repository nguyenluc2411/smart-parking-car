package com.smartparking.parking.dto.request;

import com.smartparking.parking.entity.enums.GateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request body for {@code POST /api/v1/gates/{id}/override} (docs/api-contracts.md). */
public record GateOverrideRequestDTO(
        @NotNull GateCommand command,
        @NotBlank String reason
) {
}
