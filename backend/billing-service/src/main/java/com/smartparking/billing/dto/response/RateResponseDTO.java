package com.smartparking.billing.dto.response;

import com.smartparking.billing.entity.enums.DayType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Response for {@code GET/PUT /api/v1/billing/rates}. */
public record RateResponseDTO(
        UUID id,
        BigDecimal ratePerMin,
        BigDecimal peakMultiplier,
        BigDecimal overnightFlat,
        BigDecimal minCharge,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo,
        List<ScheduleDTO> schedules
) {
    public record ScheduleDTO(int hourStart, int hourEnd, boolean isPeak, DayType dayType) {
    }
}
