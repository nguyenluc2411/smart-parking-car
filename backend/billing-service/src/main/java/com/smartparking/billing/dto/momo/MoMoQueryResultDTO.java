package com.smartparking.billing.dto.momo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response of MoMo {@code /v2/gateway/api/query}. {@code resultCode == 0} means the transaction is
 * successfully paid (and {@code transId} is MoMo's transaction id); any other code means it is not
 * yet paid (pending, expired, failed). Unknown fields ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MoMoQueryResultDTO(
        String partnerCode,
        String orderId,
        String requestId,
        Long amount,
        Long transId,
        Integer resultCode,
        String message,
        String payType,
        Long responseTime
) {
    /** MoMo uses resultCode 0 for a fully paid transaction. */
    public boolean isPaid() {
        return resultCode != null && resultCode == 0;
    }
}
