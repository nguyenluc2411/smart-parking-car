package com.smartparking.parking.service;

import com.smartparking.parking.dto.event.GateStateEventDTO;
import com.smartparking.parking.dto.request.GateOverrideRequestDTO;
import com.smartparking.parking.dto.response.GateResponseDTO;
import java.util.List;
import java.util.UUID;

/** Gate queries + manual control (BR-006-4). ADMIN only — enforced by SecurityConfig. */
public interface GateService {

    List<GateResponseDTO> listGates();

    /**
     * Manually OPEN/CLOSE a gate, log the action with the operator + reason, and emit
     * {@code parking.gate.command} via the outbox.
     *
     * @throws com.smartparking.parking.exception.ResourceNotFoundException if the gate is unknown
     */
    GateResponseDTO override(UUID gateId, GateOverrideRequestDTO request, UUID operatorId);

    /**
     * Sync {@code gates.status} from a {@code parking.gate.state} event (edge-agent). This is how the
     * BR-006-2 auto-close reaches the DB so web/mobile gate cards do not stay stuck on OPEN.
     */
    void applyGateState(GateStateEventDTO event);
}
