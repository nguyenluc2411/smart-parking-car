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
@Table(name = "parking_zones")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ParkingZone {
    @Id private UUID id;
    @Column(name = "floor_id", nullable = false) private UUID floorId;
    @Column(name = "zone_code", nullable = false, length = 10) private String zoneCode;
    @Column(nullable = false, length = 80) private String name;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
