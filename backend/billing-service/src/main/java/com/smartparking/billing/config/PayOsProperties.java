package com.smartparking.billing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PayOS payment-gateway 
 */
@Component
@ConfigurationProperties(prefix = "app.payos")
@Getter
@Setter
public class PayOsProperties {

    private String clientId;
    private String apiKey;
    private String checksumKey;
    /** Browser redirect after successful PayOS checkout (informational for demo). */
    private String returnUrl;
    /** Browser redirect when payer cancels PayOS checkout. */
    private String cancelUrl;
}
