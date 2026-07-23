package com.smartparking.billing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.smartparking.billing.dto.response.CollectionSummaryDTO;
import com.smartparking.billing.dto.response.DailyReportDTO;
import com.smartparking.billing.entity.Invoice;
import com.smartparking.billing.entity.enums.InvoiceStatus;
import com.smartparking.billing.entity.enums.PaymentMethod;
import com.smartparking.billing.repository.InvoiceRepository;
import com.smartparking.billing.repository.PaymentRepository;
import com.smartparking.billing.repository.PaymentRepository.MethodTotal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Revenue reporting — in particular the cash vs gateway split operators are audited against. */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks private ReportServiceImpl service;

    private static MethodTotal total(PaymentMethod method, String amount, long count) {
        return new MethodTotal() {
            @Override public PaymentMethod getMethod() {
                return method;
            }

            @Override public BigDecimal getAmount() {
                return new BigDecimal(amount);
            }

            @Override public long getCount() {
                return count;
            }
        };
    }

    private static Invoice invoice(String amount, OffsetDateTime exitTime) {
        return Invoice.builder()
                .id(UUID.randomUUID()).sessionId(UUID.randomUUID()).plateNumber("51F-12345")
                .amount(new BigDecimal(amount)).status(InvoiceStatus.PAID)
                .exitTime(exitTime).durationMinutes(45)
                .build();
    }

    @Test
    void daily_splitsCashFromGatewayAndKeepsOutageCashOnTheCashSide() {
        ReflectionTestUtils.setField(service, "zoneId", "Asia/Ho_Chi_Minh");
        LocalDate date = LocalDate.parse("2026-06-20");
        OffsetDateTime exit = OffsetDateTime.parse("2026-06-20T05:00:00Z");

        when(invoiceRepository.findByExitTimeInRange(any(), any()))
                .thenReturn(List.of(invoice("12000.00", exit), invoice("18000.00", exit)));
        when(paymentRepository.sumByMethodInRange(any(), any())).thenReturn(List.of(
                total(PaymentMethod.CASH, "12000.00", 1),
                total(PaymentMethod.CASH_OFFLINE, "20000.00", 2),
                total(PaymentMethod.QR_CODE, "18000.00", 1),
                total(PaymentMethod.ONLINE, "6000.00", 1)));

        DailyReportDTO report = service.daily(date);
        CollectionSummaryDTO collected = report.collected();

        // Outage cash counts as cash — it is physical notes an operator must hand in.
        assertEquals(0, collected.cashTotal().compareTo(new BigDecimal("32000.00")));
        assertEquals(0, collected.gatewayTotal().compareTo(new BigDecimal("24000.00")));
        assertEquals(0, collected.total().compareTo(new BigDecimal("56000.00")));
        assertEquals(4, collected.byMethod().size());
    }

    /**
     * Billed and collected are different figures and must not be silently reconciled: an unpaid
     * exit shows up as revenue with no matching collection, which is exactly the gap to chase.
     */
    @Test
    void daily_billedRevenueIsIndependentOfWhatWasCollected() {
        ReflectionTestUtils.setField(service, "zoneId", "Asia/Ho_Chi_Minh");
        OffsetDateTime exit = OffsetDateTime.parse("2026-06-20T05:00:00Z");

        when(invoiceRepository.findByExitTimeInRange(any(), any()))
                .thenReturn(List.of(invoice("30000.00", exit)));
        when(paymentRepository.sumByMethodInRange(any(), any())).thenReturn(List.of());

        DailyReportDTO report = service.daily(LocalDate.parse("2026-06-20"));

        assertEquals(0, report.totalRevenue().compareTo(new BigDecimal("30000.00")));
        assertEquals(0, report.collected().total().compareTo(BigDecimal.ZERO));
    }

    @Test
    void daily_noPaymentsAtAll_returnsZeroedSummaryNotNull() {
        ReflectionTestUtils.setField(service, "zoneId", "Asia/Ho_Chi_Minh");
        when(invoiceRepository.findByExitTimeInRange(any(), any())).thenReturn(List.of());
        when(paymentRepository.sumByMethodInRange(any(), any())).thenReturn(List.of());

        CollectionSummaryDTO collected = service.daily(LocalDate.parse("2026-06-20")).collected();

        assertEquals(0, collected.cashTotal().compareTo(BigDecimal.ZERO));
        assertEquals(0, collected.gatewayTotal().compareTo(BigDecimal.ZERO));
        assertEquals(List.of(), collected.byMethod());
    }
}
