package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One entry/exit captured at an auxiliary gate. The client event id makes replay idempotent, while
 * occurredAt preserves the real parking duration when a PWA synchronizes hours later.
 */
public record OutageEventRequestDTO(
        @NotNull UUID clientEventId,
        @NotNull EventType type,
        @NotBlank String plateNumber,
        @NotBlank String gateId,
        @NotNull OffsetDateTime occurredAt,
        String note
) {
    public enum EventType {
        ENTRY,
        EXIT
    }
}
