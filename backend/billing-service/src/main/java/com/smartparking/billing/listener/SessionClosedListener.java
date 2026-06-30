package com.smartparking.billing.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.billing.dto.event.SessionClosedEventDTO;
import com.smartparking.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point: consumes {@code parking.session.closed} (String JSON), deserializes it and
 * delegates to {@link BillingService} for invoice calculation.
 *
 * <p>A throw here triggers the configured retry/DLT policy ({@code KafkaConfig}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionClosedListener {

    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.session-closed}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onSessionClosed(String payload) {
        SessionClosedEventDTO event;
        try {
            event = objectMapper.readValue(payload, SessionClosedEventDTO.class);
        } catch (Exception ex) {
            log.error("Malformed parking.session.closed payload, routing to DLT: {}", payload, ex);
            throw new IllegalArgumentException("Unparseable session.closed payload", ex);
        }
        billingService.handleSessionClosed(event);
    }
}
