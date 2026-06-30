package com.smartparking.parking.entity;

import com.smartparking.parking.entity.enums.AlertSeverity;
import com.smartparking.parking.entity.enums.AlertStatus;
import com.smartparking.parking.entity.enums.AlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An operational/security anomaly surfaced to operators in real time (BR-007 — business alerts).
 * Maps table {@code alerts} in parking_db. Raised by the session flow; acknowledged via REST.
 */
@Entity
@Table(name = "alerts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 40)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(name = "plate_number", length = 20)
    private String plateNumber;

    /** Gate code (e.g. GATE_ENTRY_01) where the anomaly was seen; null if not gate-bound. */
    @Column(name = "gate_id", length = 20)
    private String gateId;

    @Column(name = "session_id")
    private UUID sessionId;

    /** Object-storage key of the captured frame (presigned for the client on read). */
    @Column(name = "image_ref", length = 300)
    private String imageRef;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = AlertStatus.NEW;
        }
        createdAt = OffsetDateTime.now();
    }
}
