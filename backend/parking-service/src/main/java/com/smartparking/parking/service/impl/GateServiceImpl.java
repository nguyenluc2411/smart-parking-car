package com.smartparking.parking.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.parking.dto.event.GateCommandEventDTO;
import com.smartparking.parking.dto.event.GateStateEventDTO;
import com.smartparking.parking.dto.request.GateOverrideRequestDTO;
import com.smartparking.parking.dto.response.GateResponseDTO;
import com.smartparking.parking.entity.Gate;
import com.smartparking.parking.entity.GateLog;
import com.smartparking.parking.entity.OutboxEvent;
import com.smartparking.parking.entity.enums.GateCommand;
import com.smartparking.parking.entity.enums.GateStatus;
import com.smartparking.parking.exception.ResourceNotFoundException;
import com.smartparking.parking.repository.GateLogRepository;
import com.smartparking.parking.repository.GateRepository;
import com.smartparking.parking.repository.OutboxEventRepository;
import com.smartparking.parking.service.GateService;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GateServiceImpl implements GateService {

    private final GateRepository gateRepository;
    private final GateLogRepository gateLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.gate-command}")
    private String gateCommandTopic;

    @Override
    @Transactional(readOnly = true)
    public List<GateResponseDTO> listGates() {
        return gateRepository.findAll().stream()
                .sorted(Comparator.comparing(Gate::getGateCode))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public GateResponseDTO override(UUID gateId, GateOverrideRequestDTO request, UUID operatorId) {
        Gate gate = gateRepository.findById(gateId)
                .orElseThrow(() -> new ResourceNotFoundException("Gate", gateId.toString()));

        GateCommand command = request.command();

        // BR-006-4: manual override must be logged with the acting admin (gate_logs has no reason
        // column, so the reason is recorded in the application log).
        gateLogRepository.save(GateLog.builder()
                .gateId(gate.getId())
                .command(command)
                .triggeredBy("ADMIN:" + operatorId)
                .build());

        gate.setStatus(command == GateCommand.OPEN ? GateStatus.OPEN : GateStatus.CLOSED);
        gate.setLastCommand(command.name());
        gate.setLastCommandAt(OffsetDateTime.now());
        gateRepository.save(gate);

        if (gate.isHasBarrier()) {
            recordGateCommand(gate, command);
        }

        log.warn("Gate override by ADMIN:{} on gate {} -> {} (reason: {})",
                operatorId, gate.getGateCode(), command, request.reason());

        return toResponse(gate);
    }

    @Override
    @Transactional
    public void applyGateState(GateStateEventDTO event) {
        GateStatus status;
        try {
            status = GateStatus.valueOf(event.status());
        } catch (IllegalArgumentException | NullPointerException ex) {
            log.warn("Ignoring parking.gate.state with unknown status '{}' for gate {}",
                    event.status(), event.gateId());
            return;
        }

        // Unknown gate = edge/topology misconfig. Skip (idempotent no-op) rather than DLT-loop.
        Gate gate = gateRepository.findByGateCode(event.gateId()).orElse(null);
        if (gate == null) {
            log.warn("parking.gate.state for unknown gate '{}', ignored", event.gateId());
            return;
        }

        if (gate.getStatus() == status) {
            return;   // already in sync, avoid redundant writes
        }
        gate.setStatus(status);   // physical state only — lastCommand keeps the issued command
        gateRepository.save(gate);
        log.info("Gate {} status synced from edge: {} (reason={})",
                gate.getGateCode(), status, event.reason());
    }

    private GateResponseDTO toResponse(Gate gate) {
        return new GateResponseDTO(gate.getId(), gate.getGateCode(), gate.getDirection(),
                gate.getStatus(), gate.isHasBarrier(), gate.getParkingLotId(), gate.getFloorId(),
                gate.getLastCommand(), gate.getLastCommandAt());
    }

    private void recordGateCommand(Gate gate, GateCommand command) {
        GateCommandEventDTO payload = GateCommandEventDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .gateId(gate.getGateCode())
                .command(command)
                .sessionId(null)               // manual override is not tied to a session
                .triggeredAt(OffsetDateTime.now())
                .build();
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("Gate")
                .aggregateId(gate.getId())
                .eventType(gateCommandTopic)
                .payload(serialize(payload))
                .build());
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
