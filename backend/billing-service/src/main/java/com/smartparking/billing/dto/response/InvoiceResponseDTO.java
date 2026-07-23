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
        InvoiceStatus status,
        /** BR-004 line items; {@code null} for invoices issued before the V6 migration. */
        BreakdownDTO breakdown,
        /**
         * The tariff constants behind the amount. Populated only on single-invoice detail views —
         * list views leave them null rather than pay an N+1 rate lookup per row.
         *
         * <p>These say what the prices WERE; {@link #breakdown} says how many of each were charged.
         * Kept alongside each other because a receipt needs both, and because the two were built
         * against different sources: the breakdown reads the snapshot stored on the invoice, these
         * fall back to the rate row the invoice references.
         */
        BigDecimal peakMultiplier,
        BigDecimal overnightFlat,
        BigDecimal minCharge
) {

    /**
     * The priced lines behind {@code amount}, so a driver can be shown why they owe it.
     *
     * <p>Line totals are computed server-side on purpose: the client must never re-derive money
     * from a rate and a count in floating point, and the tariff here is the snapshot the invoice
     * was issued with, which may no longer match the current rate table.
     */
    public record BreakdownDTO(
            int blockMinutes,
            Line normal,
            Line peak,
            Line overnight,
            /** True when the lines summed under {@code min_charge} and the total was floored. */
            boolean minChargeApplied
    ) {
        /** {@code quantity} counts blocks for normal/peak and whole nights for overnight. */
        public record Line(long quantity, BigDecimal unitAmount, BigDecimal amount) {
        }
    }
}
