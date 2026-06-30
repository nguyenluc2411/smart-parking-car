package com.smartparking.billing.dto.response;

import com.smartparking.billing.entity.enums.InvoiceStatus;
import com.smartparking.billing.entity.enums.PaymentMethod;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;

/**
 * Response for {@code POST /api/v1/driver/invoices/{invoiceId}/pay} (ADR-010). {@code qrData} is
 * null in the mock/RBL flow (no real gateway); a real integration would return a pending intent
 * carrying {@code qrData}/payUrl until confirmed by webhook.
 */
@Builder
public record DriverPaymentResponseDTO(
        UUID paymentId,
        UUID invoiceId,
        InvoiceStatus status,
        PaymentMethod method,
        BigDecimal amountPaid,
        String qrData,
        OffsetDateTime paidAt
) {
}
