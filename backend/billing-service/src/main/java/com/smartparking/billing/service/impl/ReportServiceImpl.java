package com.smartparking.billing.service.impl;

import com.smartparking.billing.dto.response.DailyReportDTO;
import com.smartparking.billing.dto.response.DailyReportDTO.HourRevenue;
import com.smartparking.billing.dto.response.MonthlyReportDTO;
import com.smartparking.billing.dto.response.MonthlyReportDTO.DayRevenue;
import com.smartparking.billing.entity.Invoice;
import com.smartparking.billing.repository.InvoiceRepository;
import com.smartparking.billing.service.ReportService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates {@code invoices} into revenue reports. "Revenue" = sum of invoice amounts (calculated
 * fee) for sessions whose exit falls in the period, evaluated in the configured zone.
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final InvoiceRepository invoiceRepository;

    @Value("${app.billing.zone-id}")
    private String zoneId;

    @Override
    @Transactional(readOnly = true)
    public DailyReportDTO daily(LocalDate date) {
        ZoneId zone = ZoneId.of(zoneId);
        OffsetDateTime from = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        List<Invoice> invoices = invoiceRepository.findByExitTimeInRange(from, to);

        int totalSessions = invoices.size();
        BigDecimal totalRevenue = sum(invoices);
        int peakSessions = (int) invoices.stream().filter(Invoice::isPeakApplied).count();
        int avgDuration = (int) Math.round(
                invoices.stream().mapToInt(Invoice::getDurationMinutes).average().orElse(0));

        Map<Integer, HourAgg> byHour = new TreeMap<>();
        for (Invoice inv : invoices) {
            int hour = inv.getExitTime().atZoneSameInstant(zone).getHour();
            byHour.computeIfAbsent(hour, h -> new HourAgg()).add(inv.getAmount());
        }
        List<HourRevenue> revenueByHour = byHour.entrySet().stream()
                .map(e -> new HourRevenue(e.getKey(), e.getValue().revenue, e.getValue().count))
                .toList();

        return new DailyReportDTO(date.toString(), totalSessions, totalRevenue, peakSessions,
                avgDuration, revenueByHour);
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyReportDTO monthly(YearMonth month) {
        ZoneId zone = ZoneId.of(zoneId);
        OffsetDateTime from = month.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime prevFrom = month.minusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();

        List<Invoice> invoices = invoiceRepository.findByExitTimeInRange(from, to);
        List<Invoice> prevInvoices = invoiceRepository.findByExitTimeInRange(prevFrom, from);

        BigDecimal totalRevenue = sum(invoices);
        BigDecimal prevRevenue = sum(prevInvoices);
        double growthRate = prevRevenue.signum() == 0
                ? (totalRevenue.signum() > 0 ? 1.0 : 0.0)
                : totalRevenue.subtract(prevRevenue)
                        .divide(prevRevenue, 4, RoundingMode.HALF_UP).doubleValue();
        BigDecimal avgDaily = totalRevenue.divide(
                BigDecimal.valueOf(month.lengthOfMonth()), 2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> byDay = new TreeMap<>();
        for (Invoice inv : invoices) {
            String day = inv.getExitTime().atZoneSameInstant(zone).toLocalDate().toString();
            byDay.merge(day, inv.getAmount(), BigDecimal::add);
        }
        List<DayRevenue> revenueByDay = byDay.entrySet().stream()
                .map(e -> new DayRevenue(e.getKey(), e.getValue()))
                .toList();

        return new MonthlyReportDTO(month.toString(), invoices.size(), totalRevenue, prevRevenue,
                growthRate, avgDaily, revenueByDay);
    }

    private static BigDecimal sum(List<Invoice> invoices) {
        return invoices.stream().map(Invoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static final class HourAgg {
        private BigDecimal revenue = BigDecimal.ZERO;
        private int count;

        void add(BigDecimal amount) {
            revenue = revenue.add(amount);
            count++;
        }
    }
}
