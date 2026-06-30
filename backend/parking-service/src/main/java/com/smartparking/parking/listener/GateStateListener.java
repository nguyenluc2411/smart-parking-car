package com.smartparking.parking.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.parking.dto.event.GateStateEventDTO;
import com.smartparking.parking.service.GateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point for barrier state sync. Consumes {@code parking.gate.state} (String JSON)
 * from edge-agent and updates {@code gates.status} via {@link GateService}.
 *
 * <p>Without this, an auto-close (BR-006-2) never reaches the DB and the web/mobile gate cards stay
 * stuck on OPEN. A throw here triggers the configured retry/DLT policy ({@code KafkaConfig}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GateStateListener {

    private final GateService gateService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.gate-state}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onGateState(String payload) {
        GateStateEventDTO event;
        try {
            event = objectMapper.readValue(payload, GateStateEventDTO.class);
        } catch (Exception ex) {
            log.error("Malformed parking.gate.state payload, routing to DLT: {}", payload, ex);
            throw new IllegalArgumentException("Unparseable gate.state payload", ex);
        }
        gateService.applyGateState(event);
    }
}
