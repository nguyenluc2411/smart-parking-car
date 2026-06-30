package com.smartparking.billing.controller;

import com.smartparking.billing.dto.response.ApiResponse;
import com.smartparking.billing.dto.response.DailyReportDTO;
import com.smartparking.billing.dto.response.MonthlyReportDTO;
import com.smartparking.billing.service.ReportService;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Revenue reports (ADMIN — enforced by SecurityConfig). */
@RestController
@RequestMapping("/api/v1/billing/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/daily")
    public ApiResponse<DailyReportDTO> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(reportService.daily(date != null ? date : LocalDate.now()));
    }

    @GetMapping("/monthly")
    public ApiResponse<MonthlyReportDTO> monthly(@RequestParam(required = false) String month) {
        YearMonth ym = month != null ? YearMonth.parse(month) : YearMonth.now();
        return ApiResponse.ok(reportService.monthly(ym));
    }
}
