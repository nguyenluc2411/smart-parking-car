package com.smartparking.parking.entity;

import com.smartparking.parking.entity.enums.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A parking session: one vehicle's stay from entry to exit.
 * Maps table {@code sessions} in parking_db. No business logic here (see CLAUDE.md §6.4).
 */
@Entity
@Table(name = "sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Column(name = "slot_id")
    private UUID slotId;

    @Column(name = "entry_gate_id")
    private UUID entryGateId;

    @Column(name = "exit_gate_id")
    private UUID exitGateId;

    @Column(name = "entry_time", nullable = false)
    private OffsetDateTime entryTime;

    @Column(name = "exit_time")
    private OffsetDateTime exitTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** Object-storage key of the snapshot captured at entry (from plate.detected IN). Nullable. */
    @Column(name = "entry_image_ref", length = 300)
    private String entryImageRef;

    /** Object-storage key of the snapshot captured at exit (from plate.detected OUT). Nullable. */
    @Column(name = "exit_image_ref", length = 300)
    private String exitImageRef;

    /** Set exactly when the exit barrier physically opens for this session (V8 migration). NULL
     * while status=CLOSED means billing has calculated the fee but the car hasn't left yet — see
     * {@code openExitGate()} in SessionServiceImpl, the single place this is written. */
    @Column(name = "exit_released_at")
    private OffsetDateTime exitReleasedAt;

    /** Client-generated idempotency keys used when an offline PWA replays outage events. */
    @Column(name = "outage_entry_event_id", unique = true)
    private UUID outageEntryEventId;

    @Column(name = "outage_exit_event_id", unique = true)
    private UUID outageExitEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
