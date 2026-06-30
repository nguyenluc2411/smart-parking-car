package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/slots/provision} — quick lot setup (ADMIN).
 * Đặt một khu (zone) có đúng {@code count} slot: hệ thống tự sinh mã {zone}01..{zone}NN,
 * tạo phần thiếu và xóa phần dư (chỉ slot không có xe).
 */
public record ProvisionZoneRequestDTO(
        @NotBlank @Size(max = 5) String zone,
        @Min(0) @Max(999) int count
) {
}
