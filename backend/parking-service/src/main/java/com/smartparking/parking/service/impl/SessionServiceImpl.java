package com.smartparking.parking.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.parking.dto.event.GateCommandEventDTO;
import com.smartparking.parking.dto.event.PlateDetectedEventDTO;
import com.smartparking.parking.dto.event.SessionClosedEventDTO;
import com.smartparking.parking.dto.event.SessionCreatedEventDTO;
import com.smartparking.parking.entity.Gate;
import com.smartparking.parking.entity.GateLog;
import com.smartparking.parking.entity.OutboxEvent;
import com.smartparking.parking.entity.Session;
import com.smartparking.parking.entity.Reservation;
import com.smartparking.parking.entity.Slot;
import com.smartparking.parking.entity.Vehicle;
import com.smartparking.parking.entity.enums.AlertSeverity;
import com.smartparking.parking.entity.enums.AlertType;
import com.smartparking.parking.entity.enums.GateCommand;
import com.smartparking.parking.entity.enums.GateStatus;
import com.smartparking.parking.entity.enums.SessionStatus;
import com.smartparking.parking.entity.enums.SlotStatus;
import com.smartparking.parking.entity.enums.VehicleType;
import com.smartparking.parking.exception.ConflictException;
import com.smartparking.parking.exception.ResourceNotFoundException;
import com.smartparking.parking.mapper.SessionMapper;
import com.smartparking.parking.repository.GateLogRepository;
import com.smartparking.parking.repository.GateRepository;
import com.smartparking.parking.repository.OutboxEventRepository;
import com.smartparking.parking.repository.SessionRepository;
import com.smartparking.parking.repository.SlotRepository;
import com.smartparking.parking.repository.VehicleRepository;
import com.smartparking.parking.service.AlertService;
import com.smartparking.parking.service.ReservationService;
import com.smartparking.parking.service.SessionService;
import com.smartparking.parking.util.PlateNumbers;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create-session use case: consumes a plate detection (entry) and, subject to the business rules,
 * opens an ACTIVE session, occupies a slot and records two outbox events to be published to Kafka.
 *
 * <p>Business rejections (blacklist, full lot, duplicate, off-hours, bad plate) are <em>expected</em>
 * outcomes: they are logged and the method returns normally so the Kafka offset commits (no retry).
 * Only infrastructure faults (DB down) or topology errors (unknown gate) throw — those are retried
 * and ultimately routed to the DLT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    /** BR-002-4: states that count as "already in the lot". */
    private static final List<SessionStatus> OPEN_STATES =
            List.of(SessionStatus.PENDING, SessionStatus.ACTIVE);

    /** BR-006-5 exit dedup: states that mean the car has already left (carry an exitTime). */
    private static final List<SessionStatus> EXITED_STATES =
            List.of(SessionStatus.CLOSED, SessionStatus.REQUIRES_ATTENTION);

    private static final String DIRECTION_IN = "IN";
    private static final String DIRECTION_OUT = "OUT";
    private static final String TRIGGERED_BY_SYSTEM = "SYSTEM";

    private final SessionRepository sessionRepository;
    private final SlotRepository slotRepository;
    private final ReservationService reservationService;
    private final VehicleRepository vehicleRepository;
    private final GateRepository gateRepository;
    private final GateLogRepository gateLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SessionMapper sessionMapper;
    private final ObjectMapper objectMapper;
    private final AlertService alertService;

    @Value("${app.kafka.topics.gate-command}")
    private String gateCommandTopic;

    @Value("${app.kafka.topics.session-created}")
    private String sessionCreatedTopic;

    @Value("${app.kafka.topics.session-closed}")
    private String sessionClosedTopic;

    /** BR-003-5: reserve a % of capacity as a drift buffer (0 = disabled). */
    @Value("${app.parking.capacity-buffer-percent:0}")
    private int capacityBufferPercent;

    /** BR-006-5: a repeat exit scan within this window is the SAME exit, ignored (0 = disabled). */
    @Value("${app.parking.exit-dedup-seconds:30}")
    private int exitDedupSeconds;

    /**
     * BR-006-5 unmatched-exit policy. {@code require-match} (default): an exit with no ACTIVE session
     * keeps the barrier CLOSED and waits for an operator (closes the forged-plate theft hole, demo-safe).
     * {@code open-always}: open the gate for any exit so a car is never trapped (safe for unattended
     * lots given imperfect OCR, but accepts the theft/revenue risk).
     */
    @Value("${app.parking.exit-policy:require-match}")
    private String exitPolicy;

    @Value("${app.parking.confidence-threshold}")
    private BigDecimal confidenceThreshold;

    @Value("${app.parking.operating-hours-start}")
    private String operatingHoursStart;

    @Value("${app.parking.operating-hours-end}")
    private String operatingHoursEnd;

    @Value("${app.parking.zone-id}")
    private String zoneId;

    @Override
    @Transactional
    public void handlePlateDetected(PlateDetectedEventDTO event) {
        log.info("plate.detected received: eventId={}, plate={}, gate={}, direction={}",
                event.eventId(), event.plateNumber(), event.gateId(), event.direction());

        String direction = event.direction();
        if (DIRECTION_IN.equalsIgnoreCase(direction)) {
            handleEntry(event);
        } else if (DIRECTION_OUT.equalsIgnoreCase(direction)) {
            // closeSession handles unmatched exits gracefully (BR-006-5) and never throws business
            // exceptions; only infra/topology errors propagate (-> retry/DLT).
            closeSession(event);
        } else {
            log.debug("Ignoring detection with unrecognised direction={}", direction);
        }
    }

    /** Entry flow (create-session). Direction is already known to be IN. */
    private void handleEntry(PlateDetectedEventDTO event) {
        // BR-001-2: confidence guard (defense in depth; edge-agent already filters).
        if (event.confidence() == null || event.confidence().compareTo(confidenceThreshold) < 0) {
            log.warn("BR-001-2: detection below confidence threshold ({} < {}), ignored",
                    event.confidence(), confidenceThreshold);
            alertService.raise(AlertType.LOW_CONFIDENCE, AlertSeverity.WARNING, event.plateNumber(),
                    event.gateId(), null, event.imageRef(),
                    "Đọc biển vào với độ tin cậy thấp (" + event.confidence() + ")");
            return;
        }

        // BR-001-4 normalize, BR-001-3 validate.
        String plate = PlateNumbers.normalize(event.plateNumber());
        if (!PlateNumbers.isValid(plate)) {
            log.warn("BR-001-3: invalid plate format '{}', ignored", event.plateNumber());
            return;
        }

        // Topology: the entry gate must exist (else misconfiguration -> retry/DLT).
        Gate gate = gateRepository.findByGateCode(event.gateId())
                .orElseThrow(() -> new ResourceNotFoundException("Gate", event.gateId()));

        // BR-008-2: no new entries outside operating hours.
        if (isOutsideOperatingHours(event.timestamp())) {
            log.warn("BR-008-2: plate '{}' denied — outside operating hours {}-{}",
                    plate, operatingHoursStart, operatingHoursEnd);
            return;
        }

        OffsetDateTime entryTime = event.timestamp() != null ? event.timestamp() : OffsetDateTime.now();
        try {
            admit(plate, gate, event.confidence(), TRIGGERED_BY_SYSTEM, entryTime, event.imageRef());
        } catch (ConflictException ex) {
            // Expected business rejections (blacklist/duplicate/full) — log + commit offset (no retry).
            log.warn("Entry denied for plate '{}': {}", plate, ex.getMessage());
        }
    }

    /**
     * Core admission shared by the event entry flow and operator manual-entry (BR-002). Plate-format
     * and confidence checks are the caller's responsibility — manual-entry bypasses the format check.
     *
     * @throws ConflictException on any business rejection (blacklist / duplicate / full)
     */
    private Session admit(String plate, Gate gate, BigDecimal confidence, String triggeredBy,
                          OffsetDateTime entryTime, String entryImageRef) {
        // BR-002-1: blacklist.
        Optional<Vehicle> vehicle = vehicleRepository.findByPlateNumber(plate);
        if (vehicle.map(v -> v.getVehicleType() == VehicleType.BLACKLIST).orElse(false)) {
            alertService.raise(AlertType.BLACKLIST_HIT, AlertSeverity.CRITICAL, plate,
                    gate.getGateCode(), null, entryImageRef, "Xe trong blacklist cố vào bãi");
            throw new ConflictException("BR-002-1: blacklisted plate " + plate + " denied entry");
        }
        // BR-002-4: one open session per plate. A plate already inside re-entering is a strong
        // cloned/forged-plate signal — alert the operator (BR-007).
        if (sessionRepository.existsByPlateNumberAndStatusIn(plate, OPEN_STATES)) {
            alertService.raise(AlertType.DUPLICATE_ACTIVE_ENTRY, AlertSeverity.CRITICAL, plate,
                    gate.getGateCode(), null, entryImageRef,
                    "Biển đang trong bãi lại quét vào — nghi biển giả/clone");
            throw new ConflictException("BR-002-4: plate " + plate + " already has an open session");
        }
        // BR-009-6: a booked driver takes the slot already held for them. Checked before the
        // capacity test — their slot is out of the pool already, so "full" must not turn them away.
        Optional<Reservation> hold = reservationService.findLiveHold(plate);
        Slot slot;
        if (hold.isPresent()) {
            slot = slotRepository.findById(hold.get().getSlotId())
                    .orElseThrow(() -> new ConflictException(
                            "BR-009-6: slot đã đặt không còn tồn tại"));
        } else {
            // BR-002-5 / BR-003-5 / BR-009-4: full once occupancy reaches capacity minus the
            // buffer. RESERVED counts as used — a held slot is promised to someone, and handing it
            // to a walk-in is exactly the failure a booking is supposed to prevent.
            long totalSlots = slotRepository.count();
            long occupiedSlots = slotRepository.countByStatus(SlotStatus.OCCUPIED)
                    + slotRepository.countByStatus(SlotStatus.RESERVED);
            long effectiveCapacity =
                    totalSlots - (long) Math.ceil(totalSlots * capacityBufferPercent / 100.0);
            if (occupiedSlots >= effectiveCapacity) {
                throw new ConflictException("BR-002-5: parking full (%d/%d occupied+reserved, buffer %d%%)"
                        .formatted(occupiedSlots, totalSlots, capacityBufferPercent));
            }
            slot = slotRepository.findFirstAvailable(SlotStatus.EMPTY, Limit.of(1))
                    .orElseThrow(() -> new ConflictException("BR-002-5: parking full — no empty slot"));
        }

        Session session = sessionRepository.save(Session.builder()
                .plateNumber(plate).slotId(slot.getId()).entryGateId(gate.getId())
                .entryTime(entryTime).status(SessionStatus.ACTIVE)
                .entryImageRef(entryImageRef).build());
        // BR-003-2: EMPTY (or the driver's RESERVED slot) -> OCCUPIED.
        slot.setStatus(SlotStatus.OCCUPIED);
        slot.setCurrentSessionId(session.getId());
        slotRepository.save(slot);
        hold.ifPresent(reservation -> reservationService.markFulfilled(reservation, session.getId()));

        // BR-006: record + reflect the OPEN command (barrier auto-closes per BR-006-2 downstream).
        gateLogRepository.save(GateLog.builder()
                .gateId(gate.getId()).sessionId(session.getId()).command(GateCommand.OPEN)
                .triggeredBy(triggeredBy).plateNumber(plate).confidence(confidence).build());
        gate.setStatus(GateStatus.OPEN);
        gate.setLastCommand(GateCommand.OPEN.name());
        gate.setLastCommandAt(OffsetDateTime.now());
        gateRepository.save(gate);

        recordOutbox("Gate", gate.getId(), gateCommandTopic, buildGateCommand(gate, session));
        recordOutbox("Session", session.getId(), sessionCreatedTopic, buildSessionCreated(session, slot, gate));

        log.info("Session created: sessionId={}, plate={}, slot={}, gate={}, by={}",
                session.getId(), plate, slot.getSlotCode(), gate.getGateCode(), triggeredBy);
        return session;
    }

    @Override
    @Transactional
    public void closeSession(PlateDetectedEventDTO event) {
        // BR-001-2 / BR-001-3: a reliable, well-formed plate is required to match the session.
        if (event.confidence() == null || event.confidence().compareTo(confidenceThreshold) < 0) {
            log.warn("BR-001-2: exit detection below confidence threshold ({} < {}), ignored",
                    event.confidence(), confidenceThreshold);
            alertService.raise(AlertType.LOW_CONFIDENCE, AlertSeverity.WARNING, event.plateNumber(),
                    event.gateId(), null, event.imageRef(),
                    "Đọc biển ra với độ tin cậy thấp (" + event.confidence() + ")");
            return;
        }
        String plate = PlateNumbers.normalize(event.plateNumber());
        if (!PlateNumbers.isValid(plate)) {
            log.warn("BR-001-3: invalid plate format on exit '{}', ignored", event.plateNumber());
            return;
        }

        // Topology: the exit gate must exist (else misconfiguration -> retry/DLT).
        Gate gate = gateRepository.findByGateCode(event.gateId())
                .orElseThrow(() -> new ResourceNotFoundException("Gate", event.gateId()));

        OffsetDateTime exitTime = event.timestamp() != null ? event.timestamp() : OffsetDateTime.now();

        // BR-002-4: at most one ACTIVE session per plate. BR-006-5: no ACTIVE session (ghost car) →
        // don't trap, don't drop — open the gate and flag REQUIRES_ATTENTION for operator review.
        Optional<Session> activeSession =
                sessionRepository.findByPlateNumberAndStatus(plate, SessionStatus.ACTIVE);
        if (activeSession.isEmpty()) {
            // Idempotency: a repeat scan of a car that JUST exited (Kafka redelivery, multi-frame
            // burst, re-trigger) has no ACTIVE session anymore. Without this it would spawn a phantom
            // REQUIRES_ATTENTION session and re-open the gate — so swallow it (BR-006-5 dedup).
            if (isDuplicateRecentExit(plate, exitTime)) {
                log.info("Duplicate exit scan for plate '{}' within {}s of its last exit — ignored",
                        plate, exitDedupSeconds);
                return;
            }
            // BR-006-5: open the gate only under the open-always policy; require-match holds it closed.
            createUnmatchedExit(plate, gate, exitTime, event.confidence(), event.imageRef(),
                    "open-always".equalsIgnoreCase(exitPolicy));
            return;
        }
        closeActive(activeSession.get(), gate, exitTime, event.confidence(), TRIGGERED_BY_SYSTEM,
                event.imageRef());
    }

    @Override
    @Transactional
    public void openExitGateForPaidSession(UUID sessionId, String plateNumber) {
        // BR-005-2 Phase 2: billing.payment.completed → release the exit barrier for a paid casual car.
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            log.warn("payment.completed for unknown sessionId={} (plate={}), ignored", sessionId, plateNumber);
            return;
        }
        if (session.getExitGateId() == null) {
            log.warn("payment.completed for session {} with no exit gate recorded — cannot open", sessionId);
            return;
        }
        Gate gate = gateRepository.findById(session.getExitGateId()).orElse(null);
        if (gate == null) {
            log.warn("payment.completed: exit gate {} not found for session {}", session.getExitGateId(), sessionId);
            return;
        }
        // Idempotent in effect: a redelivered payment event just re-opens an already-open gate.
        openExitGate(gate, session, null, "PAYMENT");
        log.info("Payment confirmed for session {} (plate {}) — opened exit gate {}",
                sessionId, session.getPlateNumber(), gate.getGateCode());
    }

    /** Core close shared by the event exit flow and operator manual-exit. */
    private void closeActive(Session session, Gate gate, OffsetDateTime exitTime,
                             BigDecimal confidence, String triggeredBy, String exitImageRef) {
        int durationSeconds =
                (int) Math.max(0, Duration.between(session.getEntryTime(), exitTime).getSeconds());

        // ACTIVE -> CLOSED. Multiple CLOSED rows per plate are allowed (partial unique idx, ADR-008).
        session.setStatus(SessionStatus.CLOSED);
        session.setExitGateId(gate.getId());
        session.setExitTime(exitTime);
        session.setDurationSeconds(durationSeconds);
        session.setExitImageRef(exitImageRef);
        sessionRepository.save(session);

        // BR-003-2: OCCUPIED -> EMPTY, release the slot.
        if (session.getSlotId() != null) {
            slotRepository.findById(session.getSlotId()).ifPresent(slot -> {
                slot.setStatus(SlotStatus.EMPTY);
                slot.setCurrentSessionId(null);
                slotRepository.save(slot);
            });
        }

        // BR-005-4: whitelist vehicles exit free — flag it so billing waives the invoice.
        boolean whitelisted = vehicleRepository.findByPlateNumber(session.getPlateNumber())
                .map(v -> v.getVehicleType() == VehicleType.WHITELIST).orElse(false);

        // Always publish session.closed so billing issues the invoice (waived for whitelist).
        recordOutbox("Session", session.getId(), sessionClosedTopic, buildSessionClosed(session, whitelisted));

        // BR-005-2 (Phase 2 — pay-before-exit): a paying (casual) car's barrier stays CLOSED until
        // billing confirms payment (billing.payment.completed -> openExitGateForPaidSession). Whitelist
        // exits free, and an operator-vouched manual exit opens immediately (a human approved it).
        boolean operatorTriggered = !TRIGGERED_BY_SYSTEM.equals(triggeredBy);
        if (whitelisted || operatorTriggered) {
            openExitGate(gate, session, confidence, triggeredBy);
            log.info("Session closed (gate opened): sessionId={}, plate={}, durationSeconds={}, "
                    + "whitelist={}, by={}", session.getId(), session.getPlateNumber(), durationSeconds,
                    whitelisted, triggeredBy);
        } else {
            log.info("Session closed (awaiting payment): sessionId={}, plate={}, durationSeconds={} — "
                    + "exit barrier held until billing.payment.completed", session.getId(),
                    session.getPlateNumber(), durationSeconds);
        }
    }

    /** BR-006-1: record + reflect the OPEN command on the exit gate (auto-closes per BR-006-2). */
    private void openExitGate(Gate gate, Session session, BigDecimal confidence, String triggeredBy) {
        gateLogRepository.save(GateLog.builder()
                .gateId(gate.getId()).sessionId(session.getId()).command(GateCommand.OPEN)
                .triggeredBy(triggeredBy).plateNumber(session.getPlateNumber()).confidence(confidence).build());
        gate.setStatus(GateStatus.OPEN);
        gate.setLastCommand(GateCommand.OPEN.name());
        gate.setLastCommandAt(OffsetDateTime.now());
        gateRepository.save(gate);
        recordOutbox("Gate", gate.getId(), gateCommandTopic, buildGateCommand(gate, session));
    }

    /**
     * BR-006-5: exit with no ACTIVE session. Always create a REQUIRES_ATTENTION session (exit-only,
     * entry placeholder) and alert the operator; no billing event is emitted (no reliable duration) —
     * an operator reconciles via {@code POST /sessions/{id}/resolve}. Shared by the event exit flow
     * and operator manual-exit.
     *
     * <p>{@code openGate} decides the barrier: {@code true} (open-always policy, or an operator-vouched
     * manual exit) opens the gate so a car is never trapped; {@code false} (require-match policy) holds
     * the barrier CLOSED and escalates to CRITICAL so an operator releases the car via gate override —
     * closing the forged-plate theft hole at the cost of possibly holding a legit car whose entry plate
     * was misread.
     */
    private Session createUnmatchedExit(String plate, Gate gate, OffsetDateTime exitTime,
                                        BigDecimal confidence, String exitImageRef, boolean openGate) {
        Session review = sessionRepository.save(Session.builder()
                .plateNumber(plate)
                .entryGateId(null)
                .exitGateId(gate.getId())
                .entryTime(exitTime)   // unknown — placeholder; operator reconciles
                .exitTime(exitTime)
                .status(SessionStatus.REQUIRES_ATTENTION)
                .exitImageRef(exitImageRef)
                .build());

        if (openGate) {
            gateLogRepository.save(GateLog.builder()
                    .gateId(gate.getId()).sessionId(review.getId()).command(GateCommand.OPEN)
                    .triggeredBy(TRIGGERED_BY_SYSTEM).plateNumber(plate).confidence(confidence).build());
            gate.setStatus(GateStatus.OPEN);
            gate.setLastCommand(GateCommand.OPEN.name());
            gate.setLastCommandAt(OffsetDateTime.now());
            gateRepository.save(gate);
            recordOutbox("Gate", gate.getId(), gateCommandTopic, buildGateCommand(gate, review));

            alertService.raise(AlertType.UNMATCHED_EXIT, AlertSeverity.WARNING, plate, gate.getGateCode(),
                    review.getId(), exitImageRef, "Xe ra không khớp session ACTIVE — đã mở barie, cần đối soát");
            log.warn("BR-006-5 [open-always]: unmatched exit for plate '{}' — opened gate {}, flagged "
                    + "REQUIRES_ATTENTION session {}", plate, gate.getGateCode(), review.getId());
        } else {
            // require-match: barrier stays CLOSED — no gate command. Operator reviews the CRITICAL alert
            // and releases the car via gate override / manual-exit.
            alertService.raise(AlertType.UNMATCHED_EXIT, AlertSeverity.CRITICAL, plate, gate.getGateCode(),
                    review.getId(), exitImageRef,
                    "Xe ra không khớp session ACTIVE — BARIE GIỮ ĐÓNG, chờ operator đối soát & Override");
            log.warn("BR-006-5 [require-match]: unmatched exit for plate '{}' — gate {} HELD CLOSED, "
                    + "flagged REQUIRES_ATTENTION session {}, awaiting operator override",
                    plate, gate.getGateCode(), review.getId());
        }
        return review;
    }

    @Override
    @Transactional
    public UUID manualEntry(String plateNumber, String gateCode, String note, UUID operatorId) {
        String plate = PlateNumbers.normalize(plateNumber);   // BR-001 format check bypassed (operator vouches)
        if (plate == null || plate.isBlank()) {
            throw new IllegalArgumentException("plateNumber is required");
        }
        Gate gate = gateRepository.findByGateCode(gateCode)
                .orElseThrow(() -> new ResourceNotFoundException("Gate", gateCode));
        Session session = admit(plate, gate, null, "OPERATOR:" + operatorId, OffsetDateTime.now(), null);
        log.warn("Manual entry by OPERATOR:{} for plate '{}' (plate-format bypassed), note: {}",
                operatorId, plate, note);
        return session.getId();
    }

    @Override
    @Transactional
    public UUID manualExit(String plateNumber, String gateCode, String note, UUID operatorId) {
        String plate = PlateNumbers.normalize(plateNumber);
        if (plate == null || plate.isBlank()) {
            throw new IllegalArgumentException("plateNumber is required");
        }
        Gate gate = gateRepository.findByGateCode(gateCode)
                .orElseThrow(() -> new ResourceNotFoundException("Gate", gateCode));
        OffsetDateTime exitTime = OffsetDateTime.now();
        String triggeredBy = "OPERATOR:" + operatorId;

        Session active = sessionRepository.findByPlateNumberAndStatus(plate, SessionStatus.ACTIVE).orElse(null);
        if (active != null) {
            closeActive(active, gate, exitTime, null, triggeredBy, null);
            log.warn("Manual exit by {} for plate '{}', note: {}", triggeredBy, plate, note);
            return active.getId();
        }
        // Operator-vouched manual exit: open the gate regardless of exit-policy (a human approved it).
        Session review = createUnmatchedExit(plate, gate, exitTime, null, null, true);
        log.warn("Manual exit by {} for plate '{}' — no ACTIVE session, flagged REQUIRES_ATTENTION {}, "
                + "note: {}", triggeredBy, plate, review.getId(), note);
        return review.getId();
    }

    private SessionClosedEventDTO buildSessionClosed(Session session, boolean whitelisted) {
        return sessionMapper.toSessionClosedEvent(session).toBuilder()
                .eventId(UUID.randomUUID().toString())
                .whitelisted(whitelisted)
                .build();
    }

    private GateCommandEventDTO buildGateCommand(Gate gate, Session session) {
        return GateCommandEventDTO.builder()
                .eventId(UUID.randomUUID().toString())
                .gateId(gate.getGateCode())
                .command(GateCommand.OPEN)
                .sessionId(session.getId())
                .triggeredAt(OffsetDateTime.now())
                .build();
    }

    private SessionCreatedEventDTO buildSessionCreated(Session session, Slot slot, Gate gate) {
        return sessionMapper.toSessionCreatedEvent(session).toBuilder()
                .eventId(UUID.randomUUID().toString())
                .slotCode(slot.getSlotCode())
                .gateId(gate.getGateCode())
                .build();
    }

    /** Serialize an event DTO and stage it in the outbox within the current transaction. */
    private void recordOutbox(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(serialize(payload))
                .build());
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Programming/serialization error — fail the TX so nothing partial is committed.
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }

    /**
     * BR-006-5 dedup: true when this plate already has an exited session (CLOSED / REQUIRES_ATTENTION)
     * whose exitTime is within {@code exitDedupSeconds} of this scan — i.e. the same physical exit
     * seen twice. Disabled when {@code exitDedupSeconds <= 0}.
     */
    private boolean isDuplicateRecentExit(String plate, OffsetDateTime exitTime) {
        if (exitDedupSeconds <= 0) {
            return false;
        }
        return sessionRepository
                .findFirstByPlateNumberAndStatusInOrderByExitTimeDesc(plate, EXITED_STATES)
                .map(Session::getExitTime)
                .filter(prev -> prev != null
                        && Math.abs(Duration.between(prev, exitTime).getSeconds()) <= exitDedupSeconds)
                .isPresent();
    }

    private boolean isOutsideOperatingHours(OffsetDateTime timestamp) {
        OffsetDateTime ts = timestamp != null ? timestamp : OffsetDateTime.now();
        LocalTime localTime = ts.atZoneSameInstant(ZoneId.of(zoneId)).toLocalTime();
        LocalTime start = LocalTime.parse(operatingHoursStart);
        LocalTime end = LocalTime.parse(operatingHoursEnd);
        // Operating window is [start, end): closed before start and at/after end.
        return localTime.isBefore(start) || !localTime.isBefore(end);
    }
}
