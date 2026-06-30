package com.smartparking.parking.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.parking.dto.request.GateOverrideRequestDTO;
import com.smartparking.parking.dto.response.GateResponseDTO;
import com.smartparking.parking.entity.Gate;
import com.smartparking.parking.entity.GateLog;
import com.smartparking.parking.entity.OutboxEvent;
import com.smartparking.parking.entity.enums.GateCommand;
import com.smartparking.parking.entity.enums.GateDirection;
import com.smartparking.parking.entity.enums.GateStatus;
import com.smartparking.parking.exception.ResourceNotFoundException;
import com.smartparking.parking.repository.GateLogRepository;
import com.smartparking.parking.repository.GateRepository;
import com.smartparking.parking.repository.OutboxEventRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GateServiceImplTest {

    @Mock private GateRepository gateRepository;
    @Mock private GateLogRepository gateLogRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private GateServiceImpl service;

    private final UUID gateId = UUID.randomUUID();
    private final UUID operator = UUID.randomUUID();

    private Gate gate() {
        return Gate.builder()
                .id(gateId).gateCode("GATE_EXIT_01").direction(GateDirection.OUT)
                .status(GateStatus.CLOSED).build();
    }

    @Test
    void override_open_logsSetsStatusAndEmitsCommand() throws Exception {
        ReflectionTestUtils.setField(service, "gateCommandTopic", "parking.gate.command");
        Gate gate = gate();
        when(gateRepository.findById(gateId)).thenReturn(Optional.of(gate));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        GateResponseDTO resp = service.override(
                gateId, new GateOverrideRequestDTO(GateCommand.OPEN, "xe kẹt"), operator);

        assertEquals(GateStatus.OPEN, resp.status());
        assertEquals(GateStatus.OPEN, gate.getStatus());
        verify(gateLogRepository).save(any(GateLog.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void override_gateNotFound_throws() {
        when(gateRepository.findById(gateId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.override(gateId, new GateOverrideRequestDTO(GateCommand.OPEN, "r"), operator));

        verify(outboxEventRepository, never()).save(any());
    }
}
