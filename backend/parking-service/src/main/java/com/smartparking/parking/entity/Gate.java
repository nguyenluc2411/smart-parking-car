package com.smartparking.parking.entity;

import com.smartparking.parking.entity.enums.GateDirection;
import com.smartparking.parking.entity.enums.GateStatus;
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
 * A barrier gate (entry/exit). Maps table {@code gates} in parking_db.
 */
@Entity
@Table(name = "gates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Gate {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "gate_code", nullable = false, unique = true, length = 20)
    private String gateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private GateDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GateStatus status;

    @Column(name = "last_command", length = 20)
    private String lastCommand;

    @Column(name = "last_command_at")
    private OffsetDateTime lastCommandAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
