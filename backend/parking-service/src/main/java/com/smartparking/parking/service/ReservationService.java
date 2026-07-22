package com.smartparking.parking.service;

import com.smartparking.parking.dto.request.CreateReservationRequestDTO;
import com.smartparking.parking.dto.response.PageResponseDTO;
import com.smartparking.parking.dto.response.ReservationResponseDTO;
import com.smartparking.parking.entity.Reservation;
import com.smartparking.parking.security.DriverPrincipal;
import java.util.Optional;
import java.util.UUID;

/** Driver slot booking (BR-009). */
public interface ReservationService {

    ReservationResponseDTO create(DriverPrincipal driver, CreateReservationRequestDTO request);

    ReservationResponseDTO cancel(DriverPrincipal driver, UUID reservationId);

    PageResponseDTO<ReservationResponseDTO> listForDriver(DriverPrincipal driver, int page, int size);

    /**
     * BR-009-6: the live hold a car arriving on {@code plateNumber} is entitled to, if any.
     * Called by the entry flow so a booked driver gets the slot they were promised.
     */
    Optional<Reservation> findLiveHold(String plateNumber);

    /** BR-009-6: mark the hold consumed by {@code sessionId}. */
    void markFulfilled(Reservation reservation, UUID sessionId);

    /** BR-009-2: release holds whose grace ran out. Returns how many were swept. */
    int expireDueHolds();
}
