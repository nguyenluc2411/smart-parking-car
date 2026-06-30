package com.smartparking.billing.dto.response;

import com.smartparking.billing.entity.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

/**
 * Response for the gate MoMo endpoints (docs/api-contracts.md):
 * <ul>
 *   <li>{@code POST /api/v1/billing/sessions/{sessionId}/momo} — returns {@code payUrl} +
 *       {@code qrCodeUrl} (render as a QR at the exit) while the invoice is still PENDING.</li>
 *   <li>{@code GET  /api/v1/billing/sessions/{sessionId}/momo/status} — returns the reconciled
 *       {@code status} (PAID once MoMo confirms the transaction).</li>
 * </ul>
 * {@code payUrl}/{@code qrCodeUrl}/{@code deeplink} are null on the status call.
 */
@Builder
public record MoMoPaymentResponseDTO(
        UUID sessionId,
        UUID invoiceId,
        BigDecimal amount,
        String orderId,
        String payUrl,
        String qrCodeUrl,
        String deeplink,
        InvoiceStatus status,
        String message
) {
}
