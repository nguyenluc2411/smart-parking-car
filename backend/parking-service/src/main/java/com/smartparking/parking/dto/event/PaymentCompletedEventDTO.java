package com.smartparking.parking.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Inbound {@code billing.payment.completed} event (BR-005-2 Phase 2 — pay-before-exit). Only the
 * fields parking-service needs to release the exit barrier are mapped; the rest (amount, method,
 * paidAt, …) are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCompletedEventDTO(
        UUID sessionId,
        String plateNumber
) {
}
