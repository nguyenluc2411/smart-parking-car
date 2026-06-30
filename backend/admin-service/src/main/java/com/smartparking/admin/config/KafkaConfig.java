package com.smartparking.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer error handling for the audit consumers. admin-service is a terminal consumer (it does
 * not produce), so a failing record is retried then logged-and-skipped (the default recoverer) —
 * we do NOT re-dead-letter, which would create {@code *.DLT.DLT} topics.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        // 3 retries, 2s apart, then log and move on.
        return new DefaultErrorHandler(new FixedBackOff(2000L, 3L));
    }
}
