package com.smartparking.billing.dto.response;

import com.smartparking.billing.entity.enums.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;

/** Response for {@code GET /api/v1/billing/report/daily} (docs/api-contracts.md). */
public record DailyReportDTO(
        String date,
        int totalSessions,
        BigDecimal totalRevenue,
        int peakSessions,
        int avgDurationMinutes,
        List<HourRevenue> revenueByHour,
        List<MethodRevenue> revenueByMethod
) {
    public record HourRevenue(int hour, BigDecimal revenue, int sessions) {
    }

    /** Revenue split by how it was collected (CASH vs QR_CODE/ONLINE = "hệ thống"). */
    public record MethodRevenue(PaymentMethod method, BigDecimal revenue, int count) {
    }
}
