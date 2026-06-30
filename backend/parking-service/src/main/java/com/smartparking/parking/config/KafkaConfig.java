package com.smartparking.parking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer error handling: retry a failing record, then route it to the per-topic Dead Letter Topic
 * ({@code <topic>.DLT}, monitored by admin-service per CLAUDE.md §5 / BR-007-1).
 *
 * <p>Note: only infrastructure/topology errors reach here — business rejections are normal returns
 * in {@code SessionServiceImpl} and never throw.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, String> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        // 3 retries, 2s apart, then publish to <topic>.DLT.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
    }
}
