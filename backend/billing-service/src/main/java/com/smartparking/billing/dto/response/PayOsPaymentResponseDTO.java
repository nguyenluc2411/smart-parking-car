package com.smartparking.billing.dto.response;

import com.smartparking.billing.entity.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

/**
 * Response for gate PayOS endpoints — mirrors {@link MoMoPaymentResponseDTO} shape for the demo UI.
 */
@Builder
public record PayOsPaymentResponseDTO(
        UUID sessionId,
        UUID invoiceId,
        BigDecimal amount,
        String orderCode,
        String checkoutUrl,
        String qrCode,
        InvoiceStatus status,
        String message
) {
}
