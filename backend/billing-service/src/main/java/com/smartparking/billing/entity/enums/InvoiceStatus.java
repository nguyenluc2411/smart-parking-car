package com.smartparking.billing.entity.enums;

/** Invoice lifecycle (DB column invoices.status). */
public enum InvoiceStatus {
    PENDING,
    PAID,
    WAIVED
}
