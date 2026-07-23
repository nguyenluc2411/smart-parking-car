package com.smartparking.billing.service.impl;

import com.smartparking.billing.dto.payos.PayOsCreateResultDTO;
import com.smartparking.billing.dto.request.CreateReservationFeeRequestDTO;
import com.smartparking.billing.dto.response.ReservationFeeResponseDTO;
import com.smartparking.billing.entity.Rate;
import com.smartparking.billing.entity.ReservationFee;
import com.smartparking.billing.entity.enums.ReservationFeeStatus;
import com.smartparking.billing.exception.InvalidPaymentException;
import com.smartparking.billing.exception.RateNotFoundException;
import com.smartparking.billing.exception.ReservationFeeNotFoundException;
import com.smartparking.billing.repository.InvoiceRepository;
import com.smartparking.billing.repository.RateRepository;
import com.smartparking.billing.repository.ReservationFeeRepository;
import com.smartparking.billing.security.DriverPrincipal;
import com.smartparking.billing.service.PayOsGateway;
import com.smartparking.billing.service.ReservationFeeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.model.webhooks.WebhookData;

/**
 * Booking fee for a driver reservation (BR-009-11): a small PayOS charge at booking time — sized
 * as a percentage of the effective rate's {@code minCharge} — to deter spam/no-show holds, given
 * back if the driver cancels early enough.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationFeeServiceImpl implements ReservationFeeService {

    private final ReservationFeeRepository reservationFeeRepository;
    private final InvoiceRepository invoiceRepository;
    private final RateRepository rateRepository;
    private final PayOsGateway payOsGateway;

    @Value("${app.billing.reservation-fee.percent-of-min-charge:0.30}")
    private BigDecimal feePercent;

    @Value("${app.billing.reservation-fee.refund-cutoff-minutes:30}")
    private int refundCutoffMinutes;

    @Override
    @Transactional
    public ReservationFeeResponseDTO create(DriverPrincipal driver, UUID reservationId,
                                            CreateReservationFeeRequestDTO request) {
        reservationFeeRepository
                .findByReservationIdAndStatusIn(reservationId,
                        List.of(ReservationFeeStatus.PENDING, ReservationFeeStatus.PAID))
                .ifPresent(existing -> {
                    throw new InvalidPaymentException(
                            "A fee already exists for this reservation (status " + existing.getStatus() + ")");
                });

        BigDecimal minCharge = rateRepository.findEffective(OffsetDateTime.now(), PageRequest.of(0, 1))
                .stream().findFirst().map(Rate::getMinCharge)
                .orElseThrow(() -> new RateNotFoundException("No effective rate configured"));
        BigDecimal amount = minCharge.multiply(feePercent).setScale(0, RoundingMode.HALF_UP);

        long orderCode = generateUniqueOrderCode();
        String description = "Phi dat cho " + request.plateNumber();
        PayOsCreateResultDTO payos = payOsGateway.createPayment(orderCode, amount.longValue(), description);

        ReservationFee fee = reservationFeeRepository.save(ReservationFee.builder()
                .reservationId(reservationId)
                .driverId(driver.driverId())
                .plateNumber(request.plateNumber())
                .reservationStartTime(request.reservationStartTime())
                .amount(amount)
                .status(ReservationFeeStatus.PENDING)
                .provider("PAYOS")
                .payosOrderCode(orderCode)
                .build());

        log.info("Reservation fee created: reservationId={}, amount={}, orderCode={}, driver={}",
                reservationId, amount, orderCode, driver.driverId());
        return toResponse(fee, payos.checkoutUrl(), payos.qrCode());
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationFeeResponseDTO get(DriverPrincipal driver, UUID reservationId) {
        ReservationFee fee = findOwned(driver, reservationId);
        return toResponse(fee, null, null);
    }

    @Override
    @Transactional
    public ReservationFeeResponseDTO refundOrForfeit(DriverPrincipal driver, UUID reservationId) {
        ReservationFee fee = findOwned(driver, reservationId);
        if (fee.getStatus() == ReservationFeeStatus.REFUNDED
                || fee.getStatus() == ReservationFeeStatus.FORFEITED) {
            log.info("Reservation fee {} already settled ({}) — idempotent no-op",
                    fee.getId(), fee.getStatus());
            return toResponse(fee, null, null);
        }
        if (fee.getStatus() == ReservationFeeStatus.PENDING) {
            // Never paid — cancelling an unpaid booking costs nothing either way.
            payOsGateway.cancelPayment(fee.getPayosOrderCode(), "Reservation cancelled");
            fee.setStatus(ReservationFeeStatus.REFUNDED);
            fee.setRefundedAt(OffsetDateTime.now());
            reservationFeeRepository.save(fee);
            return toResponse(fee, null, null);
        }

        // BR-009-11: refund only if cancelling with enough notice before the promised arrival.
        // NOTE: no real gateway refund call — PayOS/MoMo refund API isn't wired up (RBL scope);
        // this records the outcome on our side only, same as the rest of the driver-pay flow mocks.
        boolean earlyEnough = OffsetDateTime.now()
                .isBefore(fee.getReservationStartTime().minusMinutes(refundCutoffMinutes));
        fee.setStatus(earlyEnough ? ReservationFeeStatus.REFUNDED : ReservationFeeStatus.FORFEITED);
        fee.setRefundedAt(earlyEnough ? OffsetDateTime.now() : null);
        reservationFeeRepository.save(fee);
        log.info("Reservation fee {} settled on cancel: {} (cutoff {} min before {})",
                fee.getId(), fee.getStatus(), refundCutoffMinutes, fee.getReservationStartTime());
        return toResponse(fee, null, null);
    }

    @Override
    @Transactional
    public Map<String, Object> handlePayOsWebhookIfKnown(Map<String, Object> payload) {
        WebhookData verified = payOsGateway.verifyWebhook(payload);
        if (verified == null || verified.getOrderCode() == null) {
            return null;
        }
        ReservationFee fee = reservationFeeRepository
                .findByPayosOrderCodeForUpdate(verified.getOrderCode()).orElse(null);
        if (fee == null) {
            return null; // not one of ours — let the invoice webhook handler try
        }

        String code = payload.get("code") == null ? "" : String.valueOf(payload.get("code"));
        if (code.isBlank() && verified.getCode() != null) {
            code = String.valueOf(verified.getCode());
        }
        if (!"00".equals(code)) {
            log.info("PayOS webhook non-success code={} for reservation fee {}", code, fee.getId());
            return ack("payment not successful");
        }
        if (fee.getStatus() != ReservationFeeStatus.PENDING) {
            log.info("PayOS webhook: reservation fee {} already {} — idempotent no-op",
                    fee.getId(), fee.getStatus());
            return ack("already processed");
        }

        fee.setStatus(ReservationFeeStatus.PAID);
        fee.setPaidAt(OffsetDateTime.now());
        fee.setProviderRef(verified.getPaymentLinkId() != null
                ? verified.getPaymentLinkId() : String.valueOf(verified.getOrderCode()));
        reservationFeeRepository.save(fee);
        log.info("Reservation fee PAID via webhook: id={}, reservationId={}, orderCode={}",
                fee.getId(), fee.getReservationId(), verified.getOrderCode());
        return ack("success");
    }

    private ReservationFee findOwned(DriverPrincipal driver, UUID reservationId) {
        return reservationFeeRepository.findByReservationId(reservationId)
                .filter(f -> f.getDriverId().equals(driver.driverId()))
                .orElseThrow(() -> new ReservationFeeNotFoundException(
                        "No fee found for reservation " + reservationId));
    }

    private long generateUniqueOrderCode() {
        // Shared uniqueness with invoices' own PayOS order codes — both draw from the same epoch
        // second seed, so both tables must be checked (PayOS orderCode is unique per merchant, not
        // per our table).
        long orderCode = System.currentTimeMillis() / 1000;
        int attempts = 0;
        while (reservationFeeRepository.existsByPayosOrderCode(orderCode)
                || invoiceRepository.existsByPayosOrderCode(orderCode)) {
            orderCode++;
            attempts++;
            if (attempts > 10_000) {
                throw new InvalidPaymentException("Cannot generate unique PayOS orderCode");
            }
        }
        return orderCode;
    }

    private Map<String, Object> ack(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", message);
        return body;
    }

    private ReservationFeeResponseDTO toResponse(ReservationFee f, String checkoutUrl, String qrCode) {
        return new ReservationFeeResponseDTO(
                f.getId(), f.getReservationId(), f.getAmount(), f.getStatus(), f.getProvider(),
                f.getPayosOrderCode() == null ? null : String.valueOf(f.getPayosOrderCode()),
                f.getStatus() == ReservationFeeStatus.PENDING ? checkoutUrl : null,
                f.getStatus() == ReservationFeeStatus.PENDING ? qrCode : null,
                f.getPaidAt(), f.getRefundedAt());
    }
}
