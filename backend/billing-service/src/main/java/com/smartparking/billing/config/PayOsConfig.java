package com.smartparking.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

/**
 * PayOS SDK client bean. Only created when credentials are configured (non-blank client id).
 */
@Configuration
public class PayOsConfig {

    @Bean
    public PayOS payOS(PayOsProperties props) {
        if (props.getClientId() == null || props.getClientId().isBlank()) {
            return null;
        }
        return new PayOS(props.getClientId(), props.getApiKey(), props.getChecksumKey());
    }
}
