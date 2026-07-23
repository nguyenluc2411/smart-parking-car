package com.smartparking.parking.entity.enums;

/** Booking lifecycle (DB column reservations.status), BR-009. */
public enum ReservationStatus {
    /** Slot is held for the driver; the hold runs until {@code hold_until}. */
    HELD,
    /** The driver's car entered and took the held slot. */
    FULFILLED,
    /** The driver cancelled before the hold started expiring. */
    CANCELLED,
    /** The hold ran out with no car — the slot went back to the pool. Counts as a no-show. */
    EXPIRED
}
