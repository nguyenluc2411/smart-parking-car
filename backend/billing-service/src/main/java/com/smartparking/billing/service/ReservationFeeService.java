package com.smartparking.billing.service;

import com.smartparking.billing.dto.request.CreateReservationFeeRequestDTO;
import com.smartparking.billing.dto.response.ReservationFeeResponseDTO;
import com.smartparking.billing.security.DriverPrincipal;
import java.util.Map;
import java.util.UUID;

/** Booking fee for a driver reservation (BR-009-11). */
public interface ReservationFeeService {

    /** Create the fee + a PayOS payment link. Conflict if a PENDING/PAID fee already exists. */
    ReservationFeeResponseDTO create(DriverPrincipal driver, UUID reservationId,
                                     CreateReservationFeeRequestDTO request);

    ReservationFeeResponseDTO get(DriverPrincipal driver, UUID reservationId);

    /**
     * Cancel-time settlement (BR-009-11): REFUNDED if now is at least refund-cutoff-minutes before
     * {@code reservationStartTime}, otherwise FORFEITED. Idempotent no-op if already settled.
     */
    ReservationFeeResponseDTO refundOrForfeit(DriverPrincipal driver, UUID reservationId);

    /**
     * PayOS webhook, shared with the invoice flow via the same controller (BR-005 style). Returns
     * {@code null} (not {@code false} — no fee row matched) when the orderCode isn't one of ours,
     * so the caller can try the invoice flow instead without this method ever throwing 404.
     */
    Map<String, Object> handlePayOsWebhookIfKnown(Map<String, Object> payload);
}
