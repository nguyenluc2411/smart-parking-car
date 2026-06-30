package com.smartparking.billing.dto.request;

import com.smartparking.billing.entity.enums.PaymentMethod;

/**
 * Request body for {@code POST /api/v1/driver/invoices/{invoiceId}/pay} (ADR-010). Driver self-pay
 * is always {@link PaymentMethod#ONLINE}; the field is optional and informational.
 */
public record DriverPayRequestDTO(
        PaymentMethod method
) {
}
