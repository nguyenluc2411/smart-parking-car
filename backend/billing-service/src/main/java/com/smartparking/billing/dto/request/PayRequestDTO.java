package com.smartparking.billing.dto.request;

import com.smartparking.billing.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/billing/sessions/{sessionId}/pay} (docs/api-contracts.md).
 * {@code method} must be CASH or QR_CODE (BR-005-1, enforced by the enum).
 */
public record PayRequestDTO(
        @NotNull PaymentMethod method,
        @NotNull @Positive BigDecimal amountPaid,
        String note
) {
}
