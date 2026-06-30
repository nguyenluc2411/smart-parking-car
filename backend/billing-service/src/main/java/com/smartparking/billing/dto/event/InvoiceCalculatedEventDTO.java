package com.smartparking.billing.dto.event;

import com.smartparking.billing.entity.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

/**
 * Outbound event published to topic {@code billing.invoice.calculated} (consumed by admin-service).
 * Shape per docs/api-contracts.md. Kafka key = sessionId.
 */
@Builder(toBuilder = true)
public record InvoiceCalculatedEventDTO(
        String eventId,
        UUID invoiceId,
        UUID sessionId,
        String plateNumber,
        BigDecimal amount,
        boolean peakApplied,
        InvoiceStatus status
) {
}
