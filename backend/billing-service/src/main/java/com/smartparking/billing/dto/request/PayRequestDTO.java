package com.smartparking.billing.dto.request;

import com.smartparking.billing.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Request body for {@code POST /api/v1/billing/sessions/{sessionId}/pay} (docs/api-contracts.md).
 * {@code method} must be CASH, QR_CODE or CASH_OFFLINE (BR-005-1/BR-005-7, enforced by the enum).
 *
 * <p>{@code offlineVoucherNo} and {@code paidAt} only apply to CASH_OFFLINE (BR-005-7): the cash
 * changed hands during the outage but is keyed in once power is back, so the operator supplies the
 * paper voucher serial and the time the money was actually taken. Both are rejected for the other
 * methods, which are recorded live.
 */
public record PayRequestDTO(
        @NotNull PaymentMethod method,
        @NotNull @Positive BigDecimal amountPaid,
        @Size(max = 20) String offlineVoucherNo,
        OffsetDateTime paidAt,
        String note
) {
}
