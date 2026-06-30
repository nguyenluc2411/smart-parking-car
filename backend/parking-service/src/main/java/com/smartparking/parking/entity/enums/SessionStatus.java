package com.smartparking.parking.entity.enums;

/** Lifecycle of a parking session (DB column sessions.status). */
public enum SessionStatus {
    PENDING,
    ACTIVE,
    CLOSED,
    CANCELLED,
    /** BR-006-5: exit with no matching ACTIVE session — needs operator reconciliation. */
    REQUIRES_ATTENTION
}
