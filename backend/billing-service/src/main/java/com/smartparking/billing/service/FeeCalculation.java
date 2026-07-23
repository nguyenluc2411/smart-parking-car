package com.smartparking.billing.service;

import java.math.BigDecimal;

/**
 * Immutable result of a fee calculation (BR-004). Carries the breakdown the invoice needs.
 *
 * <p>The block counts are what makes an invoice explainable: {@code peakApplied} alone only says
 * "some peak was involved", which is not enough to show a driver why they owe the amount. With the
 * counts plus the rate snapshot, the receipt can be re-derived line by line without recomputing.
 */
public record FeeCalculation(
        int durationSeconds,
        int durationMinutes,
        BigDecimal ratePerMin,
        boolean peakApplied,
        boolean overnightApplied,
        BigDecimal amount,
        /** Minutes per block (BR-004-1) — snapshotted so old invoices survive a policy change. */
        int blockMinutes,
        /** Blocks charged at the normal rate, incl. night blocks let off by the grace (BR-004-3). */
        long normalBlocks,
        /** Blocks whose start fell in a peak window (BR-004-1). */
        long peakBlocks,
        /** Nights that exceeded the overnight grace and so cost one flat each (BR-004-3). */
        long overnightNights,
        /** True when the summed blocks came under {@code min_charge} and were floored (BR-004-4). */
        boolean minChargeApplied
) {
}
