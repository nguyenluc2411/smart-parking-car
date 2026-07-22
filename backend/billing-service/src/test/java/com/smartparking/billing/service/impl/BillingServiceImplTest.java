package com.smartparking.billing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.billing.dto.event.InvoiceCalculatedEventDTO;
import com.smartparking.billing.dto.event.SessionClosedEventDTO;
import com.smartparking.billing.dto.request.PayRequestDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO;
import com.smartparking.billing.dto.response.PaymentResponseDTO;
import com.smartparking.billing.entity.Invoice;
import com.smartparking.billing.entity.OutboxEvent;
import com.smartparking.billing.entity.Payment;
import com.smartparking.billing.entity.Rate;
import com.smartparking.billing.entity.enums.InvoiceStatus;
import com.smartparking.billing.entity.enums.PaymentMethod;
import com.smartparking.billing.exception.InvalidPaymentException;
import com.smartparking.billing.exception.InvoiceNotFoundException;
import com.smartparking.billing.exception.RateNotFoundException;
import com.smartparking.billing.mapper.InvoiceMapper;
import com.smartparking.billing.repository.InvoiceRepository;
import com.smartparking.billing.repository.OutboxEventRepository;
import com.smartparking.billing.repository.PaymentRepository;
import com.smartparking.billing.repository.RateRepository;
import com.smartparking.billing.repository.RateScheduleRepository;
import com.smartparking.billing.service.FeeCalculation;
import com.smartparking.billing.service.FeeCalculator;
import com.smartparking.billing.service.MoMoGateway;
import com.smartparking.billing.service.PayOsGateway;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String PLATE = "51F-12345";

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RateRepository rateRepository;
    @Mock private RateScheduleRepository rateScheduleRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private FeeCalculator feeCalculator;
    @Mock private InvoiceMapper invoiceMapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private MoMoGateway moMoGateway;
    @Mock private PayOsGateway payOsGateway;

    @InjectMocks private BillingServiceImpl service;

    private SessionClosedEventDTO event() {
        return event(false);
    }

    private SessionClosedEventDTO event(boolean whitelisted) {
        return new SessionClosedEventDTO(
                "evt-closed", SESSION_ID, PLATE,
                OffsetDateTime.parse("2026-06-20T03:00:00Z"),
                OffsetDateTime.parse("2026-06-20T03:45:00Z"), 2700, whitelisted);
    }

    private Rate rate() {
        return Rate.builder()
                .id(UUID.randomUUID())
                .ratePerMin(new BigDecimal("200.00"))
                .peakMultiplier(new BigDecimal("1.5"))
                .overnightFlat(new BigDecimal("30000.00"))
                .minCharge(new BigDecimal("5000.00"))
                .build();
    }

    @Test
    void handleSessionClosed_calculatesInvoiceAndRecordsOutbox() throws Exception {
        ReflectionTestUtils.setField(service, "invoiceCalculatedTopic", "billing.invoice.calculated");
        Rate rate = rate();
        FeeCalculation fee = new FeeCalculation(2700, 45, rate.getRatePerMin(),
                false, false, new BigDecimal("12000.00"));

        when(invoiceRepository.existsBySessionId(SESSION_ID)).thenReturn(false);
        when(rateRepository.findEffective(any(), any())).thenReturn(List.of(rate));
        when(feeCalculator.calculate(any(), any(), eq(rate), any())).thenReturn(fee);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });
        when(invoiceMapper.toInvoiceCalculatedEvent(any(Invoice.class)))
                .thenReturn(InvoiceCalculatedEventDTO.builder()
                        .sessionId(SESSION_ID).plateNumber(PLATE).status(InvoiceStatus.PENDING).build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.handleSessionClosed(event());

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertEquals(InvoiceStatus.PENDING, captor.getValue().getStatus());
        assertEquals(SESSION_ID, captor.getValue().getSessionId());
        assertEquals(0, captor.getValue().getAmount().compareTo(new BigDecimal("12000.00")));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void handleSessionClosed_whitelisted_waivesInvoice() throws Exception {
        ReflectionTestUtils.setField(service, "invoiceCalculatedTopic", "billing.invoice.calculated");
        Rate rate = rate();
        FeeCalculation fee = new FeeCalculation(2700, 45, rate.getRatePerMin(),
                false, false, new BigDecimal("12000.00"));

        when(invoiceRepository.existsBySessionId(SESSION_ID)).thenReturn(false);
        when(rateRepository.findEffective(any(), any())).thenReturn(List.of(rate));
        when(feeCalculator.calculate(any(), any(), eq(rate), any())).thenReturn(fee);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });
        when(invoiceMapper.toInvoiceCalculatedEvent(any(Invoice.class)))
                .thenReturn(InvoiceCalculatedEventDTO.builder()
                        .sessionId(SESSION_ID).plateNumber(PLATE).status(InvoiceStatus.WAIVED).build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.handleSessionClosed(event(true));

        // BR-005-4: whitelist vehicle → invoice WAIVED with amount 0 (free), still recorded.
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertEquals(InvoiceStatus.WAIVED, captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getAmount().compareTo(BigDecimal.ZERO));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void handleSessionClosed_duplicateSession_isNoOp() {
        when(invoiceRepository.existsBySessionId(SESSION_ID)).thenReturn(true);

        service.handleSessionClosed(event());

        verify(invoiceRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
        verify(rateRepository, never()).findEffective(any(), any());
    }

    @Test
    void handleSessionClosed_noEffectiveRate_throws() {
        when(invoiceRepository.existsBySessionId(SESSION_ID)).thenReturn(false);
        when(rateRepository.findEffective(any(), any())).thenReturn(List.of());

        assertThrows(RateNotFoundException.class, () -> service.handleSessionClosed(event()));

        verify(invoiceRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    /**
     * BR-004-5: "Phí tính tại thời điểm xe ra" — if the rate is changed by an admin while the car
     * is still parked, the invoice must be priced with the rate effective at EXIT time, not the
     * one effective when the car entered. There is no proration: the whole stay is billed at a
     * single rate (whichever governs at exit).
     */
    @Test
    void handleSessionClosed_rateChangedDuringStay_usesRateEffectiveAtExit() throws Exception {
        ReflectionTestUtils.setField(service, "invoiceCalculatedTopic", "billing.invoice.calculated");
        OffsetDateTime entryTime = OffsetDateTime.parse("2026-06-20T03:00:00Z"); // rateA was effective
        OffsetDateTime exitTime = OffsetDateTime.parse("2026-06-20T03:45:00Z");  // rateB now effective
        SessionClosedEventDTO event = new SessionClosedEventDTO(
                "evt-closed", SESSION_ID, PLATE, entryTime, exitTime, 2700, false);

        Rate rateB = Rate.builder()
                .id(UUID.randomUUID())
                .ratePerMin(new BigDecimal("300.00"))
                .peakMultiplier(new BigDecimal("1.5"))
                .overnightFlat(new BigDecimal("30000.00"))
                .minCharge(new BigDecimal("5000.00"))
                .build();
        FeeCalculation feeUnderRateB = new FeeCalculation(2700, 45, rateB.getRatePerMin(),
                false, false, new BigDecimal("18000.00"));

        when(invoiceRepository.existsBySessionId(SESSION_ID)).thenReturn(false);
        // Admin updated the rate mid-stay: findEffective(exitTime) must resolve to rateB, the one
        // effective NOW, regardless of what was effective back at entryTime (rateA is never queried).
        when(rateRepository.findEffective(eq(exitTime), any())).thenReturn(List.of(rateB));
        when(feeCalculator.calculate(eq(entryTime), eq(exitTime), eq(rateB), any()))
                .thenReturn(feeUnderRateB);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });
        when(invoiceMapper.toInvoiceCalculatedEvent(any(Invoice.class)))
                .thenReturn(InvoiceCalculatedEventDTO.builder()
                        .sessionId(SESSION_ID).plateNumber(PLATE).status(InvoiceStatus.PENDING).build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.handleSessionClosed(event);

        verify(rateRepository).findEffective(eq(exitTime), any());
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        // Priced with rateB (300/min), not rateA (200/min) — the whole 45-min stay, no proration.
        assertEquals(0, captor.getValue().getRatePerMin().compareTo(rateB.getRatePerMin()));
        assertEquals(0, captor.getValue().getAmount().compareTo(new BigDecimal("18000.00")));
    }

    // ----------------------------------------------------------------------------------------
    // listInvoices (operator/admin)
    // ----------------------------------------------------------------------------------------

    private InvoiceResponseDTO invoiceResponse(Invoice i) {
        return new InvoiceResponseDTO(i.getId(), i.getSessionId(), i.getPlateNumber(),
                null, null, 0, BigDecimal.ZERO, false, false, i.getAmount(), i.getStatus(),
                null, null, null);
    }

    @Test
    void listInvoices_buildsDateRangeAndMapsResults() {
        ReflectionTestUtils.setField(service, "zoneId", "Asia/Ho_Chi_Minh");
        Invoice inv = pendingInvoice();
        when(invoiceRepository.search(eq(InvoiceStatus.PENDING), eq("51F"), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(inv)));
        when(invoiceMapper.toInvoiceResponse(inv)).thenReturn(invoiceResponse(inv));

        var page = service.listInvoices(InvoiceStatus.PENDING, "51F",
                java.time.LocalDate.parse("2026-06-20"), 0, 20);

        assertEquals(1, page.content().size());
        assertEquals(PLATE, page.content().get(0).plateNumber());
        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(invoiceRepository).search(eq(InvoiceStatus.PENDING), eq("51F"),
                from.capture(), to.capture(), any());
        assertEquals(1, java.time.Duration.between(from.getValue(), to.getValue()).toDays());
    }

    @Test
    void listInvoices_blankPlateAndNoDate_usesEmptyPlateAndWideRange() {
        when(invoiceRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        service.listInvoices(null, "  ", null, 0, 20);

        // null status kept; blank plate -> "" (LIKE %% = all); no date -> wide non-null range
        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(invoiceRepository).search(eq(null), eq(""), from.capture(), to.capture(), any());
        org.junit.jupiter.api.Assertions.assertTrue(from.getValue().isBefore(to.getValue()));
    }

    // ----------------------------------------------------------------------------------------
    // pay
    // ----------------------------------------------------------------------------------------

    private Invoice pendingInvoice() {
        return Invoice.builder()
                .id(UUID.randomUUID()).sessionId(SESSION_ID).plateNumber(PLATE)
                .amount(new BigDecimal("12000.00")).status(InvoiceStatus.PENDING).build();
    }

    private PayRequestDTO payRequest(String amount) {
        return new PayRequestDTO(PaymentMethod.CASH, new BigDecimal(amount), "khách trả tròn");
    }

    @Test
    void pay_marksInvoicePaidAndEmitsEvent() throws Exception {
        ReflectionTestUtils.setField(service, "paymentCompletedTopic", "billing.payment.completed");
        Invoice invoice = pendingInvoice();
        UUID operator = UUID.randomUUID();

        when(invoiceRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(invoice));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setPaidAt(OffsetDateTime.now());
            return p;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentResponseDTO resp = service.pay(SESSION_ID, payRequest("12000.00"), operator);

        assertEquals(InvoiceStatus.PAID, resp.status());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        verify(paymentRepository).save(any(Payment.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void pay_invoiceNotFound_throws() {
        when(invoiceRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        assertThrows(InvoiceNotFoundException.class,
                () -> service.pay(SESSION_ID, payRequest("12000.00"), UUID.randomUUID()));

        verify(paymentRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void pay_alreadyPaid_throwsInvalidPayment() {
        Invoice invoice = pendingInvoice();
        invoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(invoice));

        assertThrows(InvalidPaymentException.class,
                () -> service.pay(SESSION_ID, payRequest("12000.00"), UUID.randomUUID()));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void pay_insufficientAmount_throwsInvalidPayment() {
        when(invoiceRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(pendingInvoice()));

        assertThrows(InvalidPaymentException.class,
                () -> service.pay(SESSION_ID, payRequest("5000.00"), UUID.randomUUID()));

        verify(paymentRepository, never()).save(any());
    }
}
