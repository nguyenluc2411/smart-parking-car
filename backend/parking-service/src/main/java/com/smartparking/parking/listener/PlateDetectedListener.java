package com.smartparking.parking.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.parking.dto.event.PlateDetectedEventDTO;
import com.smartparking.parking.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point for the create-session feature. Consumes {@code parking.plate.detected}
 * (String JSON), deserializes it and delegates to {@link SessionService}.
 *
 * <p>A throw here triggers the configured retry/DLT policy ({@code KafkaConfig}); a poison
 * (unparseable) message is therefore retried and ultimately dead-lettered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlateDetectedListener {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.plate-detected}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onPlateDetected(String payload) {
        PlateDetectedEventDTO event;
        try {
            event = objectMapper.readValue(payload, PlateDetectedEventDTO.class);
        } catch (Exception ex) {
            log.error("Malformed parking.plate.detected payload, routing to DLT: {}", payload, ex);
            throw new IllegalArgumentException("Unparseable plate.detected payload", ex);
        }
        sessionService.handlePlateDetected(event);
    }
}
