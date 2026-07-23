package com.smartparking.billing.entity.enums;

/** Lifecycle of a reservation booking fee (BR-009-11). DB column reservation_fees.status. */
public enum ReservationFeeStatus {
    /** Payment link created, not yet settled. */
    PENDING,
    /** Settled — the driver paid the booking fee. */
    PAID,
    /** Cancelled early enough (refund-cutoff-minutes before startTime) — fee given back. */
    REFUNDED,
    /** Cancelled too late, or no-show — fee kept, not refunded. */
    FORFEITED
}
