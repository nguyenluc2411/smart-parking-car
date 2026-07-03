package com.smartparking.billing.service;

import com.smartparking.billing.dto.payos.PayOsCreateResultDTO;
import java.util.Map;
import vn.payos.model.webhooks.WebhookData;

/**
 * Thin client over PayOS (create payment link + query + webhook verify).
 */
public interface PayOsGateway {

    /**
     * Create a PayOS payment link for a parking invoice.
     *
     * @param orderCode unique PayOS order code (long)
     * @param amount    amount in VND (integer)
     * @param description short description (max 25 chars per PayOS)
     */
    PayOsCreateResultDTO createPayment(long orderCode, long amount, String description);

    /**
     * Query PayOS payment status for an order code.
     *
     * @return true when PayOS reports PAID
     */
    boolean isPaid(long orderCode);

    /** PayOS transaction / payment link id when paid; null if unavailable. */
    String providerRef(long orderCode);

    /** Verify PayOS webhook HMAC signature via SDK. */
    WebhookData verifyWebhook(Map<String, Object> payload);
}
