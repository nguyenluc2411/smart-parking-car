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
        /**
         * Collected: payments stamped this day, split cash vs gateway.
         *
         * <p>This replaced a {@code revenueByMethod} list that grouped payments by the exit day of
         * the invoice they settled. Both answer "revenue by method", but only one can be counted
         * against a till: outage cash (BR-005-7) is taken the day the car leaves and keyed in days
         * later, so the exit-anchored figure moves money into a shift that never held it.
         */
        CollectionSummaryDTO collected
) {
    public record HourRevenue(int hour, BigDecimal revenue, int sessions) {
    }
}
