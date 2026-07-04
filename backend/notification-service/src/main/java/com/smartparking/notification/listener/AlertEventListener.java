package com.smartparking.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.notification.dto.AlertEvent;
import com.smartparking.notification.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener that consumes alert events from the {@code parking.alerts} topic
 * and routes them based on severity:
 *
 * <ul>
 *   <li><b>CRITICAL</b> (BLACKLIST_HIT, DUPLICATE_ACTIVE_ENTRY) — send Email alert to admin</li>
 *   <li><b>WARNING</b> (UNMATCHED_EXIT) — log at WARN level only</li>
 *   <li><b>INFO / LOW_CONFIDENCE</b> — log at INFO level only</li>
 * </ul>
 *
 * <p>All exceptions are caught inside the listener so that a single bad message
 * never stalls the consumer loop. Infrastructure errors (deserialization failures, etc.)
 * are handled by {@code KafkaConfig}'s DefaultErrorHandler which retries then routes to DLT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventListener {

    private final ObjectMapper objectMapper;
    private final EmailNotificationService emailService;

    @KafkaListener(
            topics = "${app.kafka.topic.alerts}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onAlertEvent(String message) {
        AlertEvent event;
        try {
            event = objectMapper.readValue(message, AlertEvent.class);
        } catch (Exception ex) {
            log.error("[AlertListener] Failed to deserialize alert message. payload='{}', error={}",
                    message, ex.getMessage(), ex);
            throw new RuntimeException("Deserialization failed for parking.alerts message", ex);
        }

        try {
            route(event);
        } catch (Exception ex) {
            log.error("[AlertListener] Unexpected error while routing alert. alertType={}, plate={}, error={}",
                    event.getAlertType(), event.getPlateNumber(), ex.getMessage(), ex);
        }
    }

    private void route(AlertEvent event) {
        String severity = event.getSeverity();

        if ("CRITICAL".equalsIgnoreCase(severity)) {
            log.info("[AlertListener] CRITICAL alert — dispatching Email. alertType={}, plate={}, gate={}",
                    event.getAlertType(), event.getPlateNumber(), event.getGateId());
            emailService.sendCriticalAlert(event);

        } else if ("WARNING".equalsIgnoreCase(severity)) {
            log.warn("[AlertListener] WARNING alert — alertType={}, plate={}, gate={}, message={}",
                    event.getAlertType(), event.getPlateNumber(), event.getGateId(), event.getMessage());

        } else {
            log.info("[AlertListener] INFO alert — alertType={}, plate={}, gate={}",
                    event.getAlertType(), event.getPlateNumber(), event.getGateId());
        }
    }
}
