package com.smartparking.billing.dto.event;

import com.smartparking.billing.entity.enums.InvoiceStatus;
import com.smartparking.billing.entity.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

/**
 * Outbound event published to topic {@code billing.payment.completed} (consumed by admin-service).
 * Kafka key = invoiceId (CLAUDE.md §5). Payload shape added to docs/api-contracts.md.
 */
@Builder
public record PaymentCompletedEventDTO(
        String eventId,
        UUID paymentId,
        UUID invoiceId,
        UUID sessionId,
        String plateNumber,
        BigDecimal amountPaid,
        PaymentMethod method,
        InvoiceStatus status,
        OffsetDateTime paidAt
) {
}
