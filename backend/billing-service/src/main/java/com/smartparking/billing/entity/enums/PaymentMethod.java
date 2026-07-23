package com.smartparking.billing.entity.enums;

/** Allowed payment methods (BR-005-1). DB column payments.method. */
public enum PaymentMethod {
    CASH,
    QR_CODE,
    /** Driver self-service online payment via the app (ADR-010). */
    ONLINE,
    /**
     * Cash taken by hand while the gate was unpowered, keyed in afterwards against a pre-printed
     * paper voucher (BR-005-7). Split out from {@link #CASH} so reports can show outage takings
     * separately — that line is the one an admin audits for shrinkage.
     */
    CASH_OFFLINE
}
