package com.smartparking.billing.entity.enums;

/** Publish state of a transactional-outbox row (DB column outbox_events.status). */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
