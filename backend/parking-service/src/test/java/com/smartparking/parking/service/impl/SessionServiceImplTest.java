package com.smartparking.parking.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.parking.dto.event.PlateDetectedEventDTO;
import com.smartparking.parking.dto.event.SessionClosedEventDTO;
import com.smartparking.parking.dto.event.SessionCreatedEventDTO;
import com.smartparking.parking.entity.Gate;
import com.smartparking.parking.entity.OutboxEvent;
import com.smartparking.parking.entity.Session;
import com.smartparking.parking.entity.Slot;
import com.smartparking.parking.entity.Vehicle;
import com.smartparking.parking.entity.enums.GateDirection;
import com.smartparking.parking.entity.enums.GateStatus;
import com.smartparking.parking.entity.enums.SessionStatus;
import com.smartparking.parking.entity.enums.SlotStatus;
import com.smartparking.parking.entity.enums.VehicleType;
import com.smartparking.parking.mapper.SessionMapper;
import com.smartparking.parking.service.AlertService;
import com.smartparking.parking.service.ReservationService;
import com.smartparking.parking.repository.GateLogRepository;
import com.smartparking.parking.repository.GateRepository;
import com.smartparking.parking.repository.OutboxEventRepository;
import com.smartparking.parking.repository.SessionRepository;
import com.smartparking.parking.repository.SlotRepository;
import com.smartparking.parking.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the entry (create-session) and exit (close-session) use cases.
 * Each test pins one business rule.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    private static final String PLATE = "51F-12345";
    private static final String ENTRY_GATE = "GATE_ENTRY_01";
    private static final String EXIT_GATE = "GATE_EXIT_01";

    // 10:00 ICT (UTC+7) — inside operating hours 06:00–22:00.
    private static final OffsetDateTime DURING_HOURS = OffsetDateTime.parse("2026-06-20T03:00:00Z");
    // 23:00 ICT — outside operating hours.
    private static final OffsetDateTime OUTSIDE_HOURS = OffsetDateTime.parse("2026-06-20T16:00:00Z");

    @Mock private SessionRepository sessionRepository;
    @Mock private SlotRepository slotRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private GateRepository gateRepository;
    @Mock private GateLogRepository gateLogRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private SessionMapper sessionMapper;
    @Mock private ObjectMapper objectMapper;
    @Mock private AlertService alertService;
    @Mock private ReservationService reservationService;

    @InjectMocks private SessionServiceImpl service;

    @BeforeEach
    void setUp() {
        // @Value fields are not injected in a plain unit test — set them explicitly.
        ReflectionTestUtils.setField(service, "gateCommandTopic", "parking.gate.command");
        ReflectionTestUtils.setField(service, "sessionCreatedTopic", "parking.session.created");
        ReflectionTestUtils.setField(service, "sessionClosedTopic", "parking.session.closed");
        ReflectionTestUtils.setField(service, "confidenceThreshold", new BigDecimal("0.85"));
        ReflectionTestUtils.setField(service, "operatingHoursStart", "06:00");
        ReflectionTestUtils.setField(service, "operatingHoursEnd", "22:00");
        ReflectionTestUtils.setField(service, "zoneId", "Asia/Ho_Chi_Minh");
        ReflectionTestUtils.setField(service, "exitDedupSeconds", 30);
    }

    private PlateDetectedEventDTO entryEvent(String plate, double confidence, OffsetDateTime ts) {
        return new PlateDetectedEventDTO(
                "evt-in", plate, BigDecimal.valueOf(confidence), ENTRY_GATE, "IN", ts, null, 400);
    }

    private PlateDetectedEventDTO exitEvent(String plate, double confidence, OffsetDateTime ts) {
        return new PlateDetectedEventDTO(
                "evt-out", plate, BigDecimal.valueOf(confidence), EXIT_GATE, "OUT", ts, null, 400);
    }

    private Gate gate(String code, GateDirection direction) {
        return Gate.builder()
                .id(UUID.randomUUID()).gateCode(code).direction(direction)
                .status(GateStatus.CLOSED).build();
    }

    // ----------------------------------------------------------------------------------------
    // create-session (entry)
    // ----------------------------------------------------------------------------------------

    @Test
    void createsSession_forRegularVehicle_duringOperatingHours() throws Exception {
        Slot slot = Slot.builder()
                .id(UUID.randomUUID()).slotCode("A01").zone("A").status(SlotStatus.EMPTY).build();

        when(gateRepository.findByGateCode(ENTRY_GATE)).thenReturn(Optional.of(gate(ENTRY_GATE, GateDirection.IN)));
        when(vehicleRepository.findByPlateNumber(PLATE)).thenReturn(Optional.empty());
        when(sessionRepository.existsByPlateNumberAndStatusIn(any(), any())).thenReturn(false);
        when(slotRepository.count()).thenReturn(10L);
        when(slotRepository.countByStatus(SlotStatus.OCCUPIED)).thenReturn(0L);
        when(slotRepository.findFirstAvailable(any(), any())).thenReturn(Optional.of(slot));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(sessionMapper.toSessionCreatedEvent(any(Session.class)))
                .thenReturn(SessionCreatedEventDTO.builder().plateNumber(PLATE).build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.handlePlateDetected(entryEvent(PLATE, 0.94, DURING_HOURS));

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals(SessionStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals(PLATE, captor.getValue().getPlateNumber());
        // Slot: EMPTY -> OCCUPIED (BR-003-2).
        assertEquals(SlotStatus.OCCUPIED, slot.getStatus());
        assertNotNull(slot.getCurrentSessionId());
        // Outbox: gate.command (OPEN) + session.created.
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));
        verify(gateLogRepository).save(any());
    }

    @Test
    void createsSession_storesEntryImageRef() throws Exception {
        Slot slot = Slot.builder()
                .id(UUID.randomUUID()).slotCode("A01").zone("A").status(SlotStatus.EMPTY).build();
        when(gateRepository.findByGateCode(ENTRY_GATE)).thenReturn(Optional.of(gate(ENTRY_GATE, GateDirection.IN)));
        when(vehicleRepository.findByPlateNumber(PLATE)).thenReturn(Optional.empty());
        when(sessionRepository.existsByPlateNumberAndStatusIn(any(), any())).thenReturn(false);
        when(slotRepository.count()).thenReturn(10L);
        when(slotRepository.countByStatus(SlotStatus.OCCUPIED)).thenReturn(0L);
        when(slotRepository.findFirstAvailable(any(), any())).thenReturn(Optional.of(slot));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(sessionMapper.toSessionCreatedEvent(any(Session.class)))
                .thenReturn(SessionCreatedEventDTO.builder().plateNumber(PLATE).build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PlateDetectedEventDTO event = new PlateDetectedEventDTO(
                "evt-in", PLATE, BigDecimal.valueOf(0.94), ENTRY_GATE, "IN", DURING_HOURS,
                "frames/2026/06/20/GATE_ENTRY_01/IN_x.jpg", 400);
        service.handlePlateDetected(event);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals("frames/2026/06/20/GATE_ENTRY_01/IN_x.jpg", captor.getValue().getEntryImageRef());
    }

    @Test
    void closeSession_storesExitImageRef() throws Exception {
        OffsetDateTime entryTime = OffsetDateTime.parse("2026-06-20T08:00:00Z");
        OffsetDateTime exitTime = OffsetDateTime.parse("2026-06-20T10:30:00Z");
        UUID sessionId = UUID.randomUUID();
        Session session = Session.builder()
                .id(sessionId).plateNumber(PLATE).slotId(null)
                .entryTime(entryTime).status(SessionStatus.ACTIVE).build();
        when(gateRepository.findByGateCode(EXIT_GATE)).thenReturn(Optional.of(gate(EXIT_GATE, GateDirection.OUT)));
        when(sessionRepository.findByPlateNumberAndStatus(PLATE, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        when(sessionMapper.toSessionClosedEvent(session))
                .thenReturn(SessionClosedEventDTO.builder().sessionId(sessionId).plateNumber(PLATE).build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        PlateDetectedEventDTO event = new PlateDetectedEventDTO(
                "evt-out", PLATE, BigDecimal.valueOf(0.96), EXIT_GATE, "OUT", exitTime,
                "frames/2026/06/20/GATE_EXIT_01/OUT_y.jpg", 400);
        service.closeSession(event);

        assertEquals("frames/2026/06/20/GATE_EXIT_01/OUT_y.jpg", session.getExitImageRef());
    }

    @Test
    void rejectsBlacklistedVehicle() {
        when(gateRepository.findByGateCode(ENTRY_GATE)).thenReturn(Optional.of(gate(ENTRY_GATE, GateDirection.IN)));
        when(vehicleRepository.findByPlateNumber(PLATE)).thenReturn(Optional.of(
                Vehicle.builder().id(UUID.randomUUID()).plateNumber(PLATE)
                        .vehicleType(VehicleType.BLACKLIST).build()));

        service.handlePlateDetected(entryEvent(PLATE, 0.94, DURING_HOURS));

        verify(sessionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateActiveSession() {
        when(gateRepository.findByGateCode(ENTRY_GATE)).thenReturn(Optional.of(gate(ENTRY_GATE, GateDirection.IN)));
        when(vehicleRepository.findByPlateNumber(PLATE)).thenReturn(Optional.empty());
        when(sessionRepository.existsByPlateNumberAndStatusIn(any(), any())).thenReturn(true);

        service.handlePlateDetected(entryEvent(PLATE, 0.94, DURING_HOURS));

        verify(sessionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void rejectsWhenLotFull() {
        when(gateRepository.findByGateCode(ENTRY_GATE)).thenReturn(Optional.of(gate(ENTRY_GATE, GateDirection.IN)));
        when(vehicleRepository.findByPlateNumber(PLATE)).thenReturn(Optional.empty());
        when(sessionRepository.existsByPlateNumberAndStatusIn(any(), any())).thenReturn(false);
        when(slotRepository.count()).thenReturn(10L);
        when(slotRepository.countByStatus(SlotStatus.OCCUPIED)).thenReturn(5L); // under capacity, buffer passes
        when(slotRepository.findFirstAvailable(any(), any())).thenReturn(Optional.empty());

        service.handlePlateDetected(entryEvent(PLATE, 0.94, DURING_HOURS));

        verify(sessionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void rejectsOutsideOperatingHours() {
        // Hours are checked before admit() (which does the vehicle lookup), so no vehicle stub here.
        when(gateRepository.findByGateCode(ENTRY_GATE)).thenReturn(Optional.of(gate(ENTRY_GATE, GateDirection.IN)));

        service.handlePlateDetected(entryEvent(PLATE, 0.94, OUTSIDE_HOURS));

        verify(sessionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void ignoresInvalidPlateFormat() {
        service.handlePlateDetected(entryEvent("not-a-plate", 0.99, DURING_HOURS));

        verify(gateRepository, never()).findByGateCode(any());
        verify(sessionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    // ----------------------------------------------------------------------------------------
    // close-session (exit)
    // ----------------------------------------------------------------------------------------

    @Test
    void closeSession_happyPath_activeToClosed() throws Exception {
        OffsetDateTime entryTime = OffsetDateTime.parse("2026-06-20T08:00:00Z");
        OffsetDateTime exitTime = OffsetDateTime.parse("2026-06-20T10:30:00Z"); // +9000s
        UUID sessionId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        Session session = Session.builder()
                .id(sessionId).plateNumber(PLATE).slotId(slotId)
                .entryTime(entryTime).status(SessionStatus.ACTIVE).build();
        Slot slot = Slot.builder()
                .id(slotId).slotCode("A01").zone("A")
                .status(SlotStatus.OCCUPIED).currentSessionId(sessionId).build();

        when(gateRepository.findByGateCode(EXIT_GATE)).thenReturn(Optional.of(gate(EXIT_GATE, GateDirection.OUT)));
        when(sessionRepository.findByPlateNumberAndStatus(PLATE, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(sessionMapper.toSessionClosedEvent(session))
                .thenReturn(SessionClosedEventDTO.builder().sessionId(sessionId).plateNumber(PLATE).build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.closeSession(exitEvent(PLATE, 0.96, exitTime));

        // Session: ACTIVE -> CLOSED with exit stamp + duration.
        assertEquals(SessionStatus.CLOSED, session.getStatus());
        assertEquals(exitTime, session.getExitTime());
        assertEquals(9000, session.getDurationSeconds());
        // Slot: OCCUPIED -> EMPTY (BR-003-2).
        assertEquals(SlotStatus.EMPTY, slot.getStatus());
        assertNull(slot.getCurrentSessionId());
        // BR-005-2 Phase 2: a casual car's barrier stays CLOSED until payment — only session.closed is
        // published (no gate.command, no gate log) here; the gate opens on billing.payment.completed.
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
        verify(gateLogRepository, never()).save(any());
    }

    @Test
    void closeSession_whitelist_opensGateImmediately() throws Exception {
        // BR-005-4: a whitelist vehicle exits free → gate opens immediately (no payment wait).
        OffsetDateTime entryTime = OffsetDateTime.parse("2026-06-20T08:00:00Z");
        OffsetDateTime exitTime = OffsetDateTime.parse("2026-06-20T09:00:00Z");
        UUID sessionId = UUID.randomUUID();
        Session session = Session.builder()
                .id(sessionId).plateNumber(PLATE)
                .entryTime(entryTime).status(SessionStatus.ACTIVE).build();

        when(gateRepository.findByGateCode(EXIT_GATE)).thenReturn(Optional.of(gate(EXIT_GATE, GateDirection.OUT)));
        when(sessionRepository.findByPlateNumberAndStatus(PLATE, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));
        when(vehicleRepository.findByPlateNumber(PLATE))
                .thenReturn(Optional.of(Vehicle.builder().plateNumber(PLATE).vehicleType(VehicleType.WHITELIST).build()));
        when(sessionMapper.toSessionClosedEvent(session))
                .thenReturn(SessionClosedEventDTO.builder().sessionId(sessionId).plateNumber(PLATE).build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.closeSession(exitEvent(PLATE, 0.96, exitTime));

        assertEquals(SessionStatus.CLOSED, session.getStatus());
        // session.closed + gate.command (OPEN) — gate opens now for the free whitelist exit.
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));
        verify(gateLogRepository).save(any());
    }

    @Test
    void openExitGateForPaidSession_opensRecordedExitGate() throws Exception {
        // BR-005-2 Phase 2: billing.payment.completed → open the paid session's exit gate.
        UUID sessionId = UUID.randomUUID();
        UUID exitGateId = UUID.randomUUID();
        Gate exitGate = gate(EXIT_GATE, GateDirection.OUT);
        exitGate.setId(exitGateId);
        Session closed = Session.builder()
                .id(sessionId).plateNumber(PLATE).status(SessionStatus.CLOSED).exitGateId(exitGateId).build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(closed));
        when(gateRepository.findById(exitGateId)).thenReturn(Optional.of(exitGate));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.openExitGateForPaidSession(sessionId, PLATE);

        // gate.command (OPEN) emitted + gate log written for the paid exit.
        verify(gateLogRepository).save(any());
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
        assertEquals(GateStatus.OPEN, exitGate.getStatus());
    }

    @Test
    void closeSession_unmatchedExit_requireMatch_holdsGateClosed() {
        // BR-006-5 require-match (default): exit with no ACTIVE session → REQUIRES_ATTENTION session
        // + CRITICAL alert, but the barrier stays CLOSED (no gate command) — operator releases via
        // override. Closes the forged-plate theft hole.
        ReflectionTestUtils.setField(service, "exitPolicy", "require-match");
        when(gateRepository.findByGateCode(EXIT_GATE)).thenReturn(Optional.of(gate(EXIT_GATE, GateDirection.OUT)));
        when(sessionRepository.findByPlateNumberAndStatus(PLATE, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        service.closeSession(exitEvent(PLATE, 0.96, OffsetDateTime.now()));

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals(SessionStatus.REQUIRES_ATTENTION, captor.getValue().getStatus());
        // Barrier held closed: no gate log and no gate-command outbox event.
        verify(gateLogRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void closeSession_unmatchedExit_openAlways_opensGate() throws Exception {
        // BR-006-5 open-always: exit with no ACTIVE session → REQUIRES_ATTENTION session + gate opened
        // so a car is never trapped.
        ReflectionTestUtils.setField(service, "exitPolicy", "open-always");
        ReflectionTestUtils.setField(service, "gateCommandTopic", "parking.gate.command");
        when(gateRepository.findByGateCode(EXIT_GATE)).thenReturn(Optional.of(gate(EXIT_GATE, GateDirection.OUT)));
        when(sessionRepository.findByPlateNumberAndStatus(PLATE, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.closeSession(exitEvent(PLATE, 0.96, OffsetDateTime.now()));

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals(SessionStatus.REQUIRES_ATTENTION, captor.getValue().getStatus());
        verify(gateLogRepository).save(any());
        // Only the gate.command outbox event (no session.closed for a phantom session).
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void closeSession_duplicateExitWithinWindow_ignored() {
        // BR-006-5 dedup: a repeat exit scan seconds after the real exit must NOT spawn a phantom
        // REQUIRES_ATTENTION session or re-open the gate.
        OffsetDateTime exitTime = OffsetDateTime.parse("2026-06-20T10:30:00Z");
        Session priorExit = Session.builder()
                .id(UUID.randomUUID()).plateNumber(PLATE)
                .status(SessionStatus.CLOSED)
                .exitTime(exitTime.minusSeconds(5))     // the car left just 5s ago
                .build();

        when(gateRepository.findByGateCode(EXIT_GATE)).thenReturn(Optional.of(gate(EXIT_GATE, GateDirection.OUT)));
        when(sessionRepository.findByPlateNumberAndStatus(PLATE, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(sessionRepository.findFirstByPlateNumberAndStatusInOrderByExitTimeDesc(any(), any()))
                .thenReturn(Optional.of(priorExit));

        service.closeSession(exitEvent(PLATE, 0.96, exitTime));

        // Fully idempotent: no phantom session, no gate command, no gate log.
        verify(sessionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
        verify(gateLogRepository, never()).save(any());
    }

    // ----------------------------------------------------------------------------------------
    // manual override (plate-format bypass)
    // ----------------------------------------------------------------------------------------

    @Test
    void manualEntry_bypassesPlateFormat_createsActiveSession() throws Exception {
        String weirdPlate = "NG-123-45"; // diplomatic — does NOT match the BR-001-3 regex
        Slot slot = Slot.builder()
                .id(UUID.randomUUID()).slotCode("A01").zone("A").status(SlotStatus.EMPTY).build();

        when(gateRepository.findByGateCode(ENTRY_GATE)).thenReturn(Optional.of(gate(ENTRY_GATE, GateDirection.IN)));
        when(vehicleRepository.findByPlateNumber("NG-123-45")).thenReturn(Optional.empty());
        when(sessionRepository.existsByPlateNumberAndStatusIn(any(), any())).thenReturn(false);
        when(slotRepository.count()).thenReturn(10L);
        when(slotRepository.countByStatus(SlotStatus.OCCUPIED)).thenReturn(0L);
        when(slotRepository.findFirstAvailable(any(), any())).thenReturn(Optional.of(slot));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(sessionMapper.toSessionCreatedEvent(any(Session.class)))
                .thenReturn(SessionCreatedEventDTO.builder().plateNumber(weirdPlate).build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        UUID id = service.manualEntry(weirdPlate, ENTRY_GATE, "Biển ngoại giao", UUID.randomUUID());

        assertNotNull(id);
        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals(SessionStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals("NG-123-45", captor.getValue().getPlateNumber()); // admitted despite non-VN format
    }

    @Test
    void manualExit_withActiveSession_closesIt() throws Exception {
        UUID slotId = UUID.randomUUID();
        Session active = Session.builder()
                .id(UUID.randomUUID()).plateNumber(PLATE).slotId(slotId)
                .entryTime(OffsetDateTime.parse("2026-06-20T08:00:00Z")).status(SessionStatus.ACTIVE).build();
        Slot slot = Slot.builder()
                .id(slotId).slotCode("A01").zone("A")
                .status(SlotStatus.OCCUPIED).currentSessionId(active.getId()).build();

        when(gateRepository.findByGateCode(EXIT_GATE)).thenReturn(Optional.of(gate(EXIT_GATE, GateDirection.OUT)));
        when(sessionRepository.findByPlateNumberAndStatus(PLATE, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(active));
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(sessionMapper.toSessionClosedEvent(active))
                .thenReturn(SessionClosedEventDTO.builder().sessionId(active.getId()).plateNumber(PLATE).build());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        UUID id = service.manualExit(PLATE, EXIT_GATE, "cho ra", UUID.randomUUID());

        assertEquals(active.getId(), id);
        assertEquals(SessionStatus.CLOSED, active.getStatus());
        assertEquals(SlotStatus.EMPTY, slot.getStatus());
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class)); // session.closed + gate.command
    }

    @Test
    void manualExit_noActiveSession_flagsReviewAttention() throws Exception {
        when(gateRepository.findByGateCode(EXIT_GATE)).thenReturn(Optional.of(gate(EXIT_GATE, GateDirection.OUT)));
        when(sessionRepository.findByPlateNumberAndStatus(PLATE, SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        UUID id = service.manualExit(PLATE, EXIT_GATE, "cho ra", UUID.randomUUID());

        assertNotNull(id);
        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals(SessionStatus.REQUIRES_ATTENTION, captor.getValue().getStatus());
        verify(outboxEventRepository).save(any(OutboxEvent.class)); // only gate.command
    }
}
