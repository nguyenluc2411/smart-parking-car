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

    /**
     * Cancel a PayOS payment link (BR-005-9): used when the invoice settles through another
     * channel (cash or MoMo) while a PayOS QR is still outstanding. Best-effort — implementations
     * must not throw on gateway/transport failure, only log, since the invoice is already settled
     * on our side regardless of whether the cancel succeeds.
     *
     * @param orderCode the PayOS order code to cancel
     * @param reason    human-readable cancellation reason shown by PayOS
     */
    void cancelPayment(long orderCode, String reason);
}
