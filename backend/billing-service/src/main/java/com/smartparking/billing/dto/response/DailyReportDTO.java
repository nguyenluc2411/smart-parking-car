package com.smartparking.billing.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Response for {@code GET /api/v1/billing/report/daily} (docs/api-contracts.md). */
public record DailyReportDTO(
        String date,
        int totalSessions,
        /** Billed: invoice amounts for sessions exiting this day (see {@link CollectionSummaryDTO}). */
        BigDecimal totalRevenue,
        int peakSessions,
        int avgDurationMinutes,
        List<HourRevenue> revenueByHour,
        /** Collected: payments stamped this day, split cash vs gateway. */
        CollectionSummaryDTO collected
) {
    public record HourRevenue(int hour, BigDecimal revenue, int sessions) {
    }
}
