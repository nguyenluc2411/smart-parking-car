package com.smartparking.billing.dto.momo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * MoMo AIO IPN callback body (POST, sent by MoMo's servers when a payment settles). Fields per the
 * MoMo spec; {@code signature} is HMAC-SHA256 over the mandated field order keyed by the partner
 * secret — verified before we trust {@code resultCode}. Unknown fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MoMoIpnRequestDTO(
        String partnerCode,
        String orderId,
        String requestId,
        Long amount,
        String orderInfo,
        String orderType,
        Long transId,
        Integer resultCode,
        String message,
        String payType,
        Long responseTime,
        String extraData,
        String signature
) {
}
