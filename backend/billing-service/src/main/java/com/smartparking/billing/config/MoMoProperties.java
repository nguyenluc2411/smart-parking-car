package com.smartparking.billing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MoMo payment-gateway config (12-Factor: injected from env, never hardcoded). Bound from
 * {@code app.momo.*}. Defaults point at the public MoMo sandbox so the demo works out of the box;
 * override in {@code .env} with a real partner's credentials for production.
 */
@Component
@ConfigurationProperties(prefix = "app.momo")
@Getter
@Setter
public class MoMoProperties {

    /** MoMo partner code (sandbox: MOMO). */
    private String partnerCode;
    /** Public access key issued with the partner code. */
    private String accessKey;
    /** HMAC-SHA256 secret used to sign requests and verify responses. */
    private String secretKey;
    /** Gateway base URL, e.g. https://test-payment.momo.vn (sandbox). */
    private String endpoint;
    /** Create-payment path appended to {@link #endpoint}. */
    private String createPath;
    /** Query-status path appended to {@link #endpoint}. */
    private String queryPath;
    /** Where MoMo redirects the payer's browser after paying (informational for the gate demo). */
    private String redirectUrl;
    /** IPN webhook MoMo calls on completion. Only reachable when tunnelled (ngrok) / deployed. */
    private String ipnUrl;
    /** MoMo request type; {@code captureWallet} returns payUrl + qrCodeUrl + deeplink. */
    private String requestType;
}
