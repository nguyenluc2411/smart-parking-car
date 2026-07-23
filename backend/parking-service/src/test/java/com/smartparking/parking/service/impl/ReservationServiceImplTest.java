package com.smartparking.parking.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartparking.parking.dto.request.CreateReservationRequestDTO;
import com.smartparking.parking.dto.response.ReservationResponseDTO;
import com.smartparking.parking.entity.Reservation;
import com.smartparking.parking.entity.Slot;
import com.smartparking.parking.entity.enums.ReservationStatus;
import com.smartparking.parking.entity.enums.SlotStatus;
import com.smartparking.parking.exception.ConflictException;
import com.smartparking.parking.exception.ForbiddenException;
import com.smartparking.parking.repository.ReservationRepository;
import com.smartparking.parking.repository.SlotRepository;
import com.smartparking.parking.security.DriverPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/** BR-009 slot booking. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceImplTest {

    private static final String PLATE = "51F-12345";
    private static final UUID DRIVER_ID = UUID.randomUUID();

    @Mock private ReservationRepository reservationRepository;
    @Mock private SlotRepository slotRepository;

    @InjectMocks private ReservationServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "holdMinutes", 20);
        ReflectionTestUtils.setField(service, "maxLeadHours", 72);
        ReflectionTestUtils.setField(service, "noShowLimit", 3);
        ReflectionTestUtils.setField(service, "noShowWindowDays", 30);
    }

    private DriverPrincipal driver() {
        return new DriverPrincipal(DRIVER_ID, List.of(PLATE));
    }

    private Slot emptySlot() {
        return Slot.builder().id(UUID.randomUUID()).slotCode("A05").zone("A")
                .status(SlotStatus.EMPTY).gridRow(0).gridCol(4).build();
    }

    private CreateReservationRequestDTO request(OffsetDateTime startTime) {
        return new CreateReservationRequestDTO(PLATE, startTime, null);
    }

    private CreateReservationRequestDTO requestForSlot(OffsetDateTime startTime, UUID slotId) {
        return new CreateReservationRequestDTO(PLATE, startTime, slotId);
    }

    /** The slot must leave the walk-in pool, or the booking promises something it cannot deliver. */
    @Test
    void create_takesTheSlotOutOfTheWalkInPool() {
        Slot slot = emptySlot();
        OffsetDateTime start = OffsetDateTime.now().plusHours(2);
        when(slotRepository.findFirstAvailable(SlotStatus.EMPTY, Limit.of(1)))
                .thenReturn(Optional.of(slot));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponseDTO resp = service.create(driver(), request(start));

        assertEquals(SlotStatus.RESERVED, slot.getStatus());
        assertEquals("A05", resp.slotCode());
        assertEquals(ReservationStatus.HELD, resp.status());
        // Hold runs 20 minutes past the promised arrival (BR-009-2).
        assertEquals(start.plusMinutes(20), resp.holdUntil());
    }

    /** BR-009-10: a driver-picked slot is claimed by id instead of auto-assigning the first EMPTY. */
    @Test
    void create_withChosenSlotId_claimsThatExactSlot() {
        Slot slot = emptySlot();
        OffsetDateTime start = OffsetDateTime.now().plusHours(2);
        when(slotRepository.findByIdAndStatus(slot.getId(), SlotStatus.EMPTY))
                .thenReturn(Optional.of(slot));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponseDTO resp = service.create(driver(), requestForSlot(start, slot.getId()));

        assertEquals(SlotStatus.RESERVED, slot.getStatus());
        assertEquals("A05", resp.slotCode());
        verify(slotRepository, never()).findFirstAvailable(any(), any());
    }

    /** BR-009-10: someone else took the chosen slot between the driver viewing the map and booking. */
    @Test
    void create_chosenSlotNoLongerEmpty_throwsConflict() {
        UUID slotId = UUID.randomUUID();
        when(slotRepository.findByIdAndStatus(slotId, SlotStatus.EMPTY))
                .thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> service.create(
                driver(), requestForSlot(OffsetDateTime.now().plusHours(1), slotId)));

        verify(reservationRepository, never()).save(any());
    }

    /** BR-009-1: a driver may only book against a plate an operator verified as theirs. */
    @Test
    void create_plateNotVerifiedForDriver_throwsForbidden() {
        DriverPrincipal other = new DriverPrincipal(DRIVER_ID, List.of("30A-99999"));

        assertThrows(ForbiddenException.class,
                () -> service.create(other, request(OffsetDateTime.now().plusHours(1))));

        verify(slotRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_secondLiveBookingForSamePlate_throwsConflict() {
        when(reservationRepository.findByPlateNumberAndStatus(PLATE, ReservationStatus.HELD))
                .thenReturn(Optional.of(Reservation.builder().build()));

        assertThrows(ConflictException.class,
                () -> service.create(driver(), request(OffsetDateTime.now().plusHours(1))));

        verify(slotRepository, never()).save(any());
    }

    @Test
    void create_noEmptySlot_throwsConflictAndBooksNothing() {
        when(slotRepository.findFirstAvailable(SlotStatus.EMPTY, Limit.of(1)))
                .thenReturn(Optional.empty());

        assertThrows(ConflictException.class,
                () -> service.create(driver(), request(OffsetDateTime.now().plusHours(1))));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_startTimeInThePast_throwsConflict() {
        assertThrows(ConflictException.class,
                () -> service.create(driver(), request(OffsetDateTime.now().minusHours(1))));
    }

    @Test
    void create_startTimeBeyondMaxLead_throwsConflict() {
        assertThrows(ConflictException.class,
                () -> service.create(driver(), request(OffsetDateTime.now().plusDays(30))));
    }

    /** BR-009-8: a plate that keeps holding slots and not turning up loses the right to book. */
    @Test
    void create_tooManyNoShows_throwsConflict() {
        when(reservationRepository.countNoShowsSince(any(), any())).thenReturn(3L);

        assertThrows(ConflictException.class,
                () -> service.create(driver(), request(OffsetDateTime.now().plusHours(1))));

        verify(slotRepository, never()).save(any());
    }

    /** An expired hold is dead the moment it lapses — not when the sweep happens to run. */
    @Test
    void findLiveHold_holdAlreadyLapsed_returnsEmpty() {
        Reservation lapsed = Reservation.builder()
                .plateNumber(PLATE).status(ReservationStatus.HELD)
                .holdUntil(OffsetDateTime.now().minusMinutes(1)).build();
        when(reservationRepository.findByPlateNumberAndStatus(PLATE, ReservationStatus.HELD))
                .thenReturn(Optional.of(lapsed));

        assertTrue(service.findLiveHold(PLATE).isEmpty());
    }

    @Test
    void findLiveHold_stillWithinGrace_returnsIt() {
        Reservation live = Reservation.builder()
                .plateNumber(PLATE).status(ReservationStatus.HELD)
                .holdUntil(OffsetDateTime.now().plusMinutes(5)).build();
        when(reservationRepository.findByPlateNumberAndStatus(PLATE, ReservationStatus.HELD))
                .thenReturn(Optional.of(live));

        assertTrue(service.findLiveHold(PLATE).isPresent());
    }

    @Test
    void expireDueHolds_releasesSlotAndMarksNoShow() {
        Slot slot = emptySlot();
        slot.setStatus(SlotStatus.RESERVED);
        Reservation due = Reservation.builder()
                .id(UUID.randomUUID()).plateNumber(PLATE).slotId(slot.getId())
                .status(ReservationStatus.HELD)
                .holdUntil(OffsetDateTime.now().minusMinutes(5)).build();
        when(reservationRepository.findExpired(any())).thenReturn(List.of(due));
        when(slotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));

        assertEquals(1, service.expireDueHolds());

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        assertEquals(ReservationStatus.EXPIRED, captor.getValue().getStatus());
        assertEquals(SlotStatus.EMPTY, slot.getStatus());
    }

    /**
     * A car may already be parked on the slot when the sweep fires (driver arrived, session took
     * the slot, the hold row lagged). Freeing it then would hand one slot to two cars.
     */
    @Test
    void expireDueHolds_slotAlreadyOccupied_leavesItAlone() {
        Slot slot = emptySlot();
        slot.setStatus(SlotStatus.OCCUPIED);
        Reservation due = Reservation.builder()
                .id(UUID.randomUUID()).plateNumber(PLATE).slotId(slot.getId())
                .status(ReservationStatus.HELD)
                .holdUntil(OffsetDateTime.now().minusMinutes(5)).build();
        when(reservationRepository.findExpired(any())).thenReturn(List.of(due));
        when(slotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));

        service.expireDueHolds();

        assertEquals(SlotStatus.OCCUPIED, slot.getStatus());
    }

    @Test
    void cancel_otherDriversBooking_throwsForbidden() {
        Reservation someoneElses = Reservation.builder()
                .id(UUID.randomUUID()).driverId(UUID.randomUUID())
                .status(ReservationStatus.HELD).build();
        when(reservationRepository.findById(someoneElses.getId()))
                .thenReturn(Optional.of(someoneElses));

        assertThrows(ForbiddenException.class,
                () -> service.cancel(driver(), someoneElses.getId()));
    }
}
