package com.smartparking.billing.service;

import com.smartparking.billing.dto.momo.MoMoCreateResultDTO;
import com.smartparking.billing.dto.momo.MoMoIpnRequestDTO;
import com.smartparking.billing.dto.momo.MoMoQueryResultDTO;

/**
 * Thin client over the MoMo AIO payment gateway (create + query). Contract only — the HMAC signing
 * and HTTP call live in the impl (CLAUDE.md §6.4).
 */
public interface MoMoGateway {

    /**
     * Create a MoMo payment request (requestType {@code captureWallet}).
     *
     * @param orderId   unique merchant order id (we use the invoice id)
     * @param requestId unique request id
     * @param amount    amount in VND (integer, MoMo min 1000)
     * @param orderInfo human-readable description shown to the payer
     * @param extraData base64 string passed through to the IPN ("" if none)
     * @return MoMo's result carrying payUrl / qrCodeUrl / deeplink when {@code resultCode == 0}
     * @throws com.smartparking.billing.exception.MoMoGatewayException on transport/parse failure
     */
    MoMoCreateResultDTO createPayment(String orderId, String requestId, long amount,
                                      String orderInfo, String extraData);

    /**
     * Query the status of a previously-created payment.
     *
     * @param orderId   the order id used at create time
     * @param requestId a request id for this query call
     * @return MoMo's result; {@link MoMoQueryResultDTO#isPaid()} is true once paid
     * @throws com.smartparking.billing.exception.MoMoGatewayException on transport/parse failure
     */
    MoMoQueryResultDTO queryPayment(String orderId, String requestId);

    /**
     * Verify a MoMo IPN callback's HMAC-SHA256 signature against the partner secret.
     *
     * @param ipn the raw IPN body MoMo POSTed
     * @return true if the signature matches (the callback is authentic)
     */
    boolean verifyIpn(MoMoIpnRequestDTO ipn);
}
