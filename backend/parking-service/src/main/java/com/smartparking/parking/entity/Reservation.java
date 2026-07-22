package com.smartparking.parking.entity;

import com.smartparking.parking.entity.enums.ReservationStatus;
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
 * A driver's slot booking (BR-009). Maps table {@code reservations} in parking_db.
 *
 * <p>{@code driverId} points at {@code admin_db.drivers} and deliberately has no FK — the services
 * own separate databases, so identity is carried in the JWT rather than joined across.
 */
@Entity
@Table(name = "reservations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    /** The hold dies here (BR-009-2). Past this the slot goes back to the walk-in pool. */
    @Column(name = "hold_until", nullable = false)
    private OffsetDateTime holdUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "session_id")
    private UUID sessionId;

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
