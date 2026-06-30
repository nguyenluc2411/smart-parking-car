package com.smartparking.billing.dto.momo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response of MoMo {@code /v2/gateway/api/create}. {@code resultCode == 0} means the payment
 * request was created; the payer then completes it via {@code payUrl} / {@code qrCodeUrl} /
 * {@code deeplink}. Unknown fields are ignored so MoMo can add fields without breaking us.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MoMoCreateResultDTO(
        String partnerCode,
        String orderId,
        String requestId,
        Long amount,
        Long responseTime,
        String message,
        Integer resultCode,
        String payUrl,
        String deeplink,
        String qrCodeUrl
) {
}
