package com.smartparking.billing.dto.response;

import com.smartparking.billing.entity.enums.InvoiceStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

/** Response for {@code POST /api/v1/billing/sessions/{sessionId}/pay} (docs/api-contracts.md). */
@Builder
public record PaymentResponseDTO(
        UUID invoiceId,
        InvoiceStatus status,
        OffsetDateTime paidAt
) {
}
