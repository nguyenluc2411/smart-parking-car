package com.smartparking.billing.dto.response;

import java.time.OffsetDateTime;

/**
 * Standard success envelope from docs/api-contracts.md:
 * {@code { success, data, message, timestamp }}.
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "OK", OffsetDateTime.now());
    }
}
