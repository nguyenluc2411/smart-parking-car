package com.smartparking.parking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SaveParkingLayoutRequestDTO(
        @Min(1) int expectedVersion,
        @Min(600) @Max(4000) int canvasWidth,
        @Min(400) @Max(3000) int canvasHeight,
        @NotNull @Size(max = 2000) List<@Valid Element> elements
) {
    public record Element(
            @NotNull UUID id,
            @NotNull ElementType type,
            UUID referenceId,
            String label,
            double x,
            double y,
            double width,
            double height,
            double rotation,
            Map<String, String> properties
    ) {
    }

    public enum ElementType {
        SLOT,
        GATE,
        BARRIER,
        ROAD,
        LABEL,
        ZONE
    }
}
