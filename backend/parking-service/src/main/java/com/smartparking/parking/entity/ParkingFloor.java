package com.smartparking.parking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "parking_floors")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ParkingFloor {
    @Id private UUID id;
    @Column(name = "parking_lot_id", nullable = false) private UUID parkingLotId;
    @Column(name = "floor_code", nullable = false, length = 20) private String floorCode;
    @Column(nullable = false, length = 80) private String name;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
