package com.smartparking.parking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BR-009-2: sweeps holds whose grace has run out back into the walk-in pool.
 *
 * <p>The sweep is a safety net, not the source of truth — {@code findLiveHold} already treats an
 * expired hold as dead, so a late or missed run never lets a no-show claim their slot. What the
 * sweep does is free the slot for someone else, which is why it runs often and cheaply.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${app.parking.reservation.sweep-ms:60000}")
    public void sweep() {
        try {
            int expired = reservationService.expireDueHolds();
            if (expired > 0) {
                log.info("Reservation sweep: {} hold(s) expired and released", expired);
            }
        } catch (Exception exc) {
            // Never let a failed sweep kill the scheduler thread — the next run retries.
            log.error("Reservation sweep failed", exc);
        }
    }
}
