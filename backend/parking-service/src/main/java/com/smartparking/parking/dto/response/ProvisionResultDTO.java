package com.smartparking.parking.dto.response;

/** Result of {@code POST /api/v1/slots/provision}: số slot đã tạo/xóa và tổng toàn bãi. */
public record ProvisionResultDTO(
        String zone,
        int zoneTotal,
        int created,
        int removed,
        long total
) {
}
