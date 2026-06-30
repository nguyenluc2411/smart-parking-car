package com.smartparking.billing.dto.response;

import com.smartparking.billing.entity.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response for {@code GET /api/v1/billing/sessions/{sessionId}} (docs/api-contracts.md). */
public record InvoiceResponseDTO(
        UUID invoiceId,
        UUID sessionId,
        String plateNumber,
        OffsetDateTime entryTime,
        OffsetDateTime exitTime,
        Integer durationMinutes,
        BigDecimal ratePerMin,
        boolean peakApplied,
        boolean overnightApplied,
        BigDecimal amount,
        InvoiceStatus status
) {
}
