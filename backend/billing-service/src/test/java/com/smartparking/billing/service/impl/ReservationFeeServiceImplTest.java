package com.smartparking.billing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartparking.billing.dto.payos.PayOsCreateResultDTO;
import com.smartparking.billing.dto.request.CreateReservationFeeRequestDTO;
import com.smartparking.billing.dto.response.ReservationFeeResponseDTO;
import com.smartparking.billing.entity.Rate;
import com.smartparking.billing.entity.ReservationFee;
import com.smartparking.billing.entity.enums.ReservationFeeStatus;
import com.smartparking.billing.exception.InvalidPaymentException;
import com.smartparking.billing.exception.ReservationFeeNotFoundException;
import com.smartparking.billing.repository.InvoiceRepository;
import com.smartparking.billing.repository.RateRepository;
import com.smartparking.billing.repository.ReservationFeeRepository;
import com.smartparking.billing.security.DriverPrincipal;
import com.smartparking.billing.service.PayOsGateway;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** BR-009-11 reservation booking fee. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationFeeServiceImplTest {

    private static final UUID DRIVER_ID = UUID.randomUUID();
    private static final UUID RESERVATION_ID = UUID.randomUUID();

    @Mock private ReservationFeeRepository reservationFeeRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private RateRepository rateRepository;
    @Mock private PayOsGateway payOsGateway;

    @InjectMocks private ReservationFeeServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "feePercent", new BigDecimal("0.30"));
        ReflectionTestUtils.setField(service, "refundCutoffMinutes", 30);
    }

    private DriverPrincipal driver() {
        return new DriverPrincipal(DRIVER_ID, List.of("51F-12345"));
    }

    private Rate rateWithMinCharge(String minCharge) {
        return Rate.builder().id(UUID.randomUUID()).minCharge(new BigDecimal(minCharge))
                .ratePerMin(BigDecimal.ONE).peakMultiplier(BigDecimal.ONE)
                .overnightFlat(BigDecimal.ZERO).effectiveFrom(OffsetDateTime.now().minusDays(1))
                .build();
    }

    /** Fee = feePercent * minCharge, rounded — 30% of 15000 = 4500. */
    @Test
    void create_computesFeeAsPercentOfMinCharge() {
        when(rateRepository.findEffective(any(), any())).thenReturn(List.of(rateWithMinCharge("15000")));
        when(reservationFeeRepository.findByReservationIdAndStatusIn(any(), any()))
                .thenReturn(Optional.empty());
        when(payOsGateway.createPayment(anyLong(), anyLong(), anyString()))
                .thenReturn(PayOsCreateResultDTO.builder()
                        .orderCode(123L).checkoutUrl("https://pay").qrCode("qr").build());
        when(reservationFeeRepository.save(any(ReservationFee.class)))
                .thenAnswer(i -> i.getArgument(0));

        ReservationFeeResponseDTO resp = service.create(driver(), RESERVATION_ID,
                new CreateReservationFeeRequestDTO("51F-12345", OffsetDateTime.now().plusHours(2)));

        assertEquals(new BigDecimal("4500"), resp.amount());
        assertEquals(ReservationFeeStatus.PENDING, resp.status());
        assertEquals("https://pay", resp.checkoutUrl());
    }

    @Test
    void create_feeAlreadyExists_throwsConflict() {
        when(reservationFeeRepository.findByReservationIdAndStatusIn(any(), any()))
                .thenReturn(Optional.of(ReservationFee.builder()
                        .status(ReservationFeeStatus.PENDING).build()));

        assertThrows(InvalidPaymentException.class, () -> service.create(driver(), RESERVATION_ID,
                new CreateReservationFeeRequestDTO("51F-12345", OffsetDateTime.now().plusHours(2))));

        verify(payOsGateway, never()).createPayment(anyLong(), anyLong(), anyString());
    }

    /** BR-009-11: cancelling well before the cutoff refunds the fee. */
    @Test
    void refund_earlyEnough_isRefunded() {
        ReservationFee fee = ReservationFee.builder()
                .id(UUID.randomUUID()).reservationId(RESERVATION_ID).driverId(DRIVER_ID)
                .status(ReservationFeeStatus.PAID)
                .reservationStartTime(OffsetDateTime.now().plusHours(1))
                .amount(new BigDecimal("4500"))
                .build();
        when(reservationFeeRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(fee));
        when(reservationFeeRepository.save(any(ReservationFee.class))).thenAnswer(i -> i.getArgument(0));

        ReservationFeeResponseDTO resp = service.refundOrForfeit(driver(), RESERVATION_ID);

        assertEquals(ReservationFeeStatus.REFUNDED, resp.status());
    }

    /** BR-009-11: cancelling inside the cutoff window forfeits the fee. */
    @Test
    void refund_tooLate_isForfeited() {
        ReservationFee fee = ReservationFee.builder()
                .id(UUID.randomUUID()).reservationId(RESERVATION_ID).driverId(DRIVER_ID)
                .status(ReservationFeeStatus.PAID)
                .reservationStartTime(OffsetDateTime.now().plusMinutes(10))
                .amount(new BigDecimal("4500"))
                .build();
        when(reservationFeeRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(fee));
        when(reservationFeeRepository.save(any(ReservationFee.class))).thenAnswer(i -> i.getArgument(0));

        ReservationFeeResponseDTO resp = service.refundOrForfeit(driver(), RESERVATION_ID);

        assertEquals(ReservationFeeStatus.FORFEITED, resp.status());
    }

    /** An unpaid (PENDING) fee costs nothing to cancel either way. */
    @Test
    void refund_stillPending_isRefundedAndCancelsPayOsLink() {
        ReservationFee fee = ReservationFee.builder()
                .id(UUID.randomUUID()).reservationId(RESERVATION_ID).driverId(DRIVER_ID)
                .status(ReservationFeeStatus.PENDING).payosOrderCode(123L)
                .reservationStartTime(OffsetDateTime.now().plusMinutes(5))
                .amount(new BigDecimal("4500"))
                .build();
        when(reservationFeeRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(fee));
        when(reservationFeeRepository.save(any(ReservationFee.class))).thenAnswer(i -> i.getArgument(0));

        ReservationFeeResponseDTO resp = service.refundOrForfeit(driver(), RESERVATION_ID);

        assertEquals(ReservationFeeStatus.REFUNDED, resp.status());
        verify(payOsGateway).cancelPayment(123L, "Reservation cancelled");
    }

    @Test
    void get_notOwnedByDriver_throwsNotFound() {
        ReservationFee fee = ReservationFee.builder()
                .id(UUID.randomUUID()).reservationId(RESERVATION_ID).driverId(UUID.randomUUID())
                .status(ReservationFeeStatus.PAID).build();
        when(reservationFeeRepository.findByReservationId(RESERVATION_ID)).thenReturn(Optional.of(fee));

        assertThrows(ReservationFeeNotFoundException.class,
                () -> service.get(driver(), RESERVATION_ID));
    }
}
