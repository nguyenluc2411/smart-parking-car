package com.smartparking.billing.service;

import com.smartparking.billing.dto.response.DailyReportDTO;
import com.smartparking.billing.dto.response.MonthlyReportDTO;
import java.time.LocalDate;
import java.time.YearMonth;

/** Revenue reporting ({@code GET /api/v1/billing/report/*}). ADMIN only. */
public interface ReportService {

    DailyReportDTO daily(LocalDate date);

    MonthlyReportDTO monthly(YearMonth month);
}
