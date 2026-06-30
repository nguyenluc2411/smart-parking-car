package com.smartparking.billing.service;

import java.math.BigDecimal;

/**
 * Immutable result of a fee calculation (BR-004). Carries the breakdown the invoice needs.
 */
public record FeeCalculation(
        int durationSeconds,
        int durationMinutes,
        BigDecimal ratePerMin,
        boolean peakApplied,
        boolean overnightApplied,
        BigDecimal amount
) {
}
