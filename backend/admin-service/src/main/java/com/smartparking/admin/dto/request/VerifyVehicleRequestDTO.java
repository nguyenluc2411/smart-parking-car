package com.smartparking.admin.dto.request;

import jakarta.validation.constraints.NotNull;

/** {@code POST /api/v1/driver-vehicles/{id}/verify}. {@code approved=false} rejects (removes) it. */
public record VerifyVehicleRequestDTO(
        @NotNull
        Boolean approved
) {
}
