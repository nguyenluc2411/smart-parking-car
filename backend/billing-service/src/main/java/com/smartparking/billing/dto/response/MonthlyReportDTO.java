package com.smartparking.billing.dto.response;

import com.smartparking.billing.dto.response.DailyReportDTO.MethodRevenue;
import java.math.BigDecimal;
import java.util.List;

/** Response for {@code GET /api/v1/billing/report/monthly}. */
public record MonthlyReportDTO(
        String month,
        int totalSessions,
        BigDecimal totalRevenue,
        BigDecimal prevMonthRevenue,
        double growthRate,
        BigDecimal avgDailyRevenue,
        List<DayRevenue> revenueByDay,
        List<MethodRevenue> revenueByMethod
) {
    public record DayRevenue(String date, BigDecimal revenue) {
    }
}
