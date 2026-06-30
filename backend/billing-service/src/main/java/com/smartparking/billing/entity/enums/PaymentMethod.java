package com.smartparking.billing.entity.enums;

/** Allowed payment methods (BR-005-1). DB column payments.method. */
public enum PaymentMethod {
    CASH,
    QR_CODE,
    /** Driver self-service online payment via the app (ADR-010). */
    ONLINE
}
