package com.smartparking.parking.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "parking_layouts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParkingLayout {
    @Id
    private UUID id;

    @Column(name = "parking_lot_id", nullable = false)
    private UUID parkingLotId;

    @Column(name = "floor_id", nullable = false, unique = true)
    private UUID floorId;

    @Column(name = "canvas_width", nullable = false)
    private int canvasWidth;

    @Column(name = "canvas_height", nullable = false)
    private int canvasHeight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_elements", nullable = false, columnDefinition = "jsonb")
    private JsonNode draftElements;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "published_elements", nullable = false, columnDefinition = "jsonb")
    private JsonNode publishedElements;

    @Column(name = "draft_version", nullable = false)
    private int draftVersion;

    @Column(name = "published_version", nullable = false)
    private int publishedVersion;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (canvasWidth <= 0) canvasWidth = 1200;
        if (canvasHeight <= 0) canvasHeight = 720;
        if (draftElements == null) draftElements = JsonNodeFactory.instance.arrayNode();
        if (publishedElements == null) publishedElements = JsonNodeFactory.instance.arrayNode();
        if (draftVersion <= 0) draftVersion = 1;
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
