package com.smartparking.parking.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.parking.dto.event.PaymentCompletedEventDTO;
import com.smartparking.parking.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * BR-005-2 (Phase 2 — pay-before-exit). Consumes {@code billing.payment.completed} (String JSON) and
 * releases the exit barrier for the paid session. Uses the parking consumer group, so admin-service
 * (its own group) still receives the event for audit.
 *
 * <p>A throw here triggers the configured retry/DLT policy; an unparseable message is dead-lettered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedListener {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.payment-completed}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentCompleted(String payload) {
        PaymentCompletedEventDTO event;
        try {
            event = objectMapper.readValue(payload, PaymentCompletedEventDTO.class);
        } catch (Exception ex) {
            log.error("Malformed billing.payment.completed payload, routing to DLT: {}", payload, ex);
            throw new IllegalArgumentException("Unparseable payment.completed payload", ex);
        }
        if (event.sessionId() == null) {
            log.warn("billing.payment.completed with no sessionId, ignored: {}", payload);
            return;
        }
        sessionService.openExitGateForPaidSession(event.sessionId(), event.plateNumber());
    }
}
