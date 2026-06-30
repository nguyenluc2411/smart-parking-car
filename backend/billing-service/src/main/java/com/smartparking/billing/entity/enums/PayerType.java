package com.smartparking.billing.entity.enums;

/** Who made a payment (DB column payments.payer_type, ADR-010). */
public enum PayerType {
    /** Collected at the gate by an operator (CASH / QR_CODE); {@code received_by} is the operator. */
    OPERATOR,
    /** Paid online by the driver via the app (ONLINE); {@code driver_id} is set, {@code received_by} null. */
    DRIVER
}
