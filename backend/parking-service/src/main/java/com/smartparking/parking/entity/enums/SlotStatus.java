package com.smartparking.parking.entity.enums;

/** Slot availability state (DB column slots.status). */
public enum SlotStatus {
    EMPTY,
    OCCUPIED,
    /**
     * Held for a driver's booking (BR-009). Never handed to a walk-in, and counted as used when
     * testing whether the lot is full — otherwise the lot is oversold and the booking is worthless.
     */
    RESERVED,
    MAINTENANCE
}
