package com.smartparking.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer error handling: retry a failing record up to 3 times (2 s apart),
 * then route it to the per-topic Dead Letter Topic ({@code <topic>.DLT}).
 *
 * <p>Only infrastructure/deserialization errors reach here — business routing
 * (CRITICAL / WARNING / INFO) is handled normally inside
 * {@code AlertEventListener} and never throws.
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
        // 3 retries, 2 s apart, then publish to <topic>.DLT.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
    }
}
