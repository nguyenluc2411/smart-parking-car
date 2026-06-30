package com.smartparking.parking.entity.enums;

/** Kind of operational/security anomaly surfaced to operators (alerts table). */
public enum AlertType {
    /** A plate already ACTIVE in the lot scanned at entry again — possible cloned/forged plate (BR-002-4). */
    DUPLICATE_ACTIVE_ENTRY,
    /** A blacklisted plate was denied entry (BR-002-1). */
    BLACKLIST_HIT,
    /** Exit with no matching ACTIVE session (BR-006-5) — misread or a car that entered unseen. */
    UNMATCHED_EXIT,
    /** ALPR read below the confidence threshold (BR-001-2). */
    LOW_CONFIDENCE
}
