package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/sessions/manual-entry} and {@code /manual-exit} — operator
 * admits/releases a vehicle whose plate does not match the BR-001-3 regex (format check bypassed).
 */
public record ManualGateRequestDTO(
        @NotBlank String plateNumber,
        @NotBlank String gateId,   // gate_code, e.g. GATE_ENTRY_01
        String note
) {
}
