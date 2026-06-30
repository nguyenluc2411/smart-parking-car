package com.smartparking.billing.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Request body for {@code PUT /api/v1/billing/rates} (ADMIN). Creates a new effective rate version. */
public record UpdateRateRequestDTO(
        @NotNull @Positive BigDecimal ratePerMin,
        @NotNull @Positive BigDecimal peakMultiplier,
        @NotNull @PositiveOrZero BigDecimal overnightFlat,
        @NotNull @PositiveOrZero BigDecimal minCharge
) {
}
