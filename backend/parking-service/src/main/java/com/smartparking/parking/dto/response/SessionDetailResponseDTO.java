package com.smartparking.parking.dto.response;

import com.smartparking.parking.entity.enums.SessionStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Response for {@code GET /api/v1/sessions/{id}} (docs/api-contracts.md). */
public record SessionDetailResponseDTO(
        UUID id,
        String plateNumber,
        SlotRef slot,
        GateRef entryGate,
        GateRef exitGate,
        OffsetDateTime entryTime,
        OffsetDateTime exitTime,
        Integer durationSeconds,
        SessionStatus status,
        /** Non-null once the exit barrier has physically opened for this session (BR-005-5). */
        OffsetDateTime exitReleasedAt,
        String entryImageUrl,        // presigned URL ảnh full chụp lúc vào, đã vẽ khung+biển (null nếu không có)
        String exitImageUrl,         // presigned URL ảnh full chụp lúc ra, đã vẽ khung+biển (null nếu không có)
        String entryPlateImageUrl,   // presigned URL ảnh crop cận cảnh biển lúc vào (null nếu không có)
        String exitPlateImageUrl     // presigned URL ảnh crop cận cảnh biển lúc ra (null nếu không có)
) {
    public record SlotRef(UUID id, String slotCode, String zone) {
    }

    public record GateRef(UUID id, String gateCode) {
    }
}
