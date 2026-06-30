package com.smartparking.parking.service;

import com.smartparking.parking.dto.event.PlateDetectedEventDTO;
import java.util.UUID;

/**
 * Entry/exit orchestration for parking sessions (event-driven + operator manual override).
 *
 * <p>Contract only — no business logic, no {@code @Transactional} here (CLAUDE.md §6.4).
 */
public interface SessionService {

    /**
     * Handle a plate detection from edge-agent. For {@code direction = IN} this applies the
     * access-control rules (BR-001/002/003/008) and, when admitted, opens a session, occupies a
     * slot and emits {@code parking.gate.command} (OPEN) + {@code parking.session.created} via the
     * outbox. {@code direction = OUT} is handled by the (separate) close-session feature.
     *
     * @param event the {@code parking.plate.detected} payload
     */
    void handlePlateDetected(PlateDetectedEventDTO event);

    /**
     * Close the open session for an exiting vehicle ({@code direction = OUT}): match the ACTIVE
     * session by plate (BR-002-4), stamp exit time + duration to status CLOSED, free the slot
     * (BR-003-2), and emit {@code parking.session.closed} + {@code parking.gate.command} (OPEN,
     * exit gate) via the outbox.
     *
     * <p>BR-006-5: if there is no ACTIVE session (ghost car / mis-read entry) the car is NOT trapped
     * — the gate is opened and a REQUIRES_ATTENTION session is flagged for operator reconciliation;
     * no business exception is thrown.
     *
     * @param event the {@code parking.plate.detected} payload with {@code direction = OUT}
     */
    void closeSession(PlateDetectedEventDTO event);

    /**
     * Operator manual entry — admit a vehicle whose plate does NOT match the BR-001-3 regex
     * (diplomatic NG/NN, foreign, damaged) or that ALPR failed to read. Bypasses the plate-format
     * check (operator vouches) but still applies BR-002 (blacklist/duplicate/capacity).
     *
     * @return the new session id
     * @throws com.smartparking.parking.exception.ConflictException if blacklisted / duplicate / full
     */
    UUID manualEntry(String plateNumber, String gateCode, String note, UUID operatorId);

    /**
     * Operator manual exit — let a vehicle out by plate. Closes the ACTIVE session if found; otherwise
     * flags a REQUIRES_ATTENTION session (BR-006-5) and opens the gate.
     *
     * @return the affected session id
     */
    UUID manualExit(String plateNumber, String gateCode, String note, UUID operatorId);

    /**
     * BR-005-2 (Phase 2 — pay-before-exit): release the exit barrier for a casual vehicle once billing
     * confirms payment. Triggered by the {@code billing.payment.completed} event: looks up the closed
     * session by id, finds its recorded exit gate and emits {@code parking.gate.command} (OPEN).
     * No-op (logged) if the session or its exit gate is unknown.
     *
     * @param sessionId  the paid session's id (from the payment event)
     * @param plateNumber the plate (for logging/audit only)
     */
    void openExitGateForPaidSession(UUID sessionId, String plateNumber);
}
