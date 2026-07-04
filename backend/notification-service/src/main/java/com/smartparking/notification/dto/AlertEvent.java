package com.smartparking.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO representing a parking alert event consumed from the {@code parking.alerts} Kafka topic.
 *
 * <p>Unknown JSON fields are silently ignored so that producers can evolve the schema
 * without breaking this consumer.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code alertType} — {@code BLACKLIST_HIT | DUPLICATE_ACTIVE_ENTRY | UNMATCHED_EXIT | LOW_CONFIDENCE}</li>
 *   <li>{@code severity}  — {@code CRITICAL | WARNING | INFO}</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertEvent {

    /** Unique identifier of this alert. */
    private UUID id;

    /** Type of alert (e.g. BLACKLIST_HIT, DUPLICATE_ACTIVE_ENTRY, UNMATCHED_EXIT, LOW_CONFIDENCE). */
    private String alertType;

    /** Severity level: CRITICAL, WARNING, or INFO. */
    private String severity;

    /** License plate number that triggered the alert. */
    private String plateNumber;

    /** Gate identifier where the event occurred. */
    private String gateId;

    /** Parking session UUID — may be null for some alert types. */
    private UUID sessionId;

    /** Human-readable description of the incident. */
    private String message;

    /** Timestamp when the alert was created (ISO-8601 with zone offset). */
    private OffsetDateTime createdAt;
}
