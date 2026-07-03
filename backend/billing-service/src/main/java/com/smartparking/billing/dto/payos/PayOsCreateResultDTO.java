package com.smartparking.billing.dto.payos;

import lombok.Builder;

/** Result from PayOS paymentRequests().create(). */
@Builder
public record PayOsCreateResultDTO(
        long orderCode,
        String checkoutUrl,
        String qrCode,
        String paymentLinkId
) {
}
