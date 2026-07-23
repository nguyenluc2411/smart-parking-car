package com.smartparking.billing.dto.response;

import com.smartparking.billing.entity.enums.ReservationFeeStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A reservation booking fee (BR-009-11). */
public record ReservationFeeResponseDTO(
        UUID id,
        UUID reservationId,
        BigDecimal amount,
        ReservationFeeStatus status,
        String provider,
        String orderCode,
        /** PayOS payment link, null once PAID/REFUNDED/FORFEITED. */
        String checkoutUrl,
        String qrCode,
        OffsetDateTime paidAt,
        OffsetDateTime refundedAt
) {
}
