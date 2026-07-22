package com.smartparking.parking.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartparking.parking.dto.request.ProvisionZoneRequestDTO;
import com.smartparking.parking.dto.request.UpdateSlotStatusRequestDTO;
import com.smartparking.parking.dto.response.SlotAvailabilityResponseDTO;
import com.smartparking.parking.dto.response.SlotResyncResultDTO;
import com.smartparking.parking.entity.Slot;
import com.smartparking.parking.entity.enums.ReservationStatus;
import com.smartparking.parking.entity.enums.SessionStatus;
import com.smartparking.parking.entity.enums.SlotStatus;
import com.smartparking.parking.exception.ConflictException;
import com.smartparking.parking.repository.ReservationRepository;
import com.smartparking.parking.repository.SessionRepository;
import com.smartparking.parking.repository.SlotRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlotServiceImplTest {

    @Mock private SlotRepository slotRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private ReservationRepository reservationRepository;
    @InjectMocks private SlotServiceImpl service;

    @Test
    void getAvailability_computesCountsAndRate() {
        when(slotRepository.count()).thenReturn(50L);
        when(slotRepository.countByStatus(SlotStatus.OCCUPIED)).thenReturn(32L);
        when(slotRepository.countByStatus(SlotStatus.RESERVED)).thenReturn(0L);
        when(slotRepository.countByStatus(SlotStatus.EMPTY)).thenReturn(18L);
        when(slotRepository.countByStatus(SlotStatus.MAINTENANCE)).thenReturn(0L);

        SlotAvailabilityResponseDTO dto = service.getAvailability();

        assertEquals(50L, dto.totalSlots());
        assertEquals(32L, dto.occupiedSlots());
        assertEquals(18L, dto.emptySlots());
        assertEquals(0.64, dto.occupancyRate());
    }

    /** BR-009-4: a held slot cannot take a walk-in, so the gauge must count it as used. */
    @Test
    void getAvailability_countsReservedAsUsed() {
        when(slotRepository.count()).thenReturn(50L);
        when(slotRepository.countByStatus(SlotStatus.OCCUPIED)).thenReturn(32L);
        when(slotRepository.countByStatus(SlotStatus.RESERVED)).thenReturn(8L);
        when(slotRepository.countByStatus(SlotStatus.EMPTY)).thenReturn(10L);
        when(slotRepository.countByStatus(SlotStatus.MAINTENANCE)).thenReturn(0L);

        SlotAvailabilityResponseDTO dto = service.getAvailability();

        assertEquals(8L, dto.reservedSlots());
        assertEquals(10L, dto.emptySlots());
        assertEquals(0.80, dto.occupancyRate());
    }

    @Test
    void getAvailability_emptyLot_rateIsZero() {
        when(slotRepository.count()).thenReturn(0L);
        when(slotRepository.countByStatus(SlotStatus.OCCUPIED)).thenReturn(0L);
        when(slotRepository.countByStatus(SlotStatus.RESERVED)).thenReturn(0L);
        when(slotRepository.countByStatus(SlotStatus.EMPTY)).thenReturn(0L);
        when(slotRepository.countByStatus(SlotStatus.MAINTENANCE)).thenReturn(0L);

        assertEquals(0.0, service.getAvailability().occupancyRate());
    }

    /**
     * BR-003-4 + BR-009-4: resync's ground truth is ACTIVE sessions, and a live hold has none yet.
     * Without the hold check it would free the slot and hand it to the next walk-in.
     */
    @Test
    void resync_leavesSlotsUnderALiveHoldReserved() {
        UUID heldSlotId = UUID.randomUUID();
        Slot held = Slot.builder().slotCode("A05").zone("A").status(SlotStatus.RESERVED).build();
        held.setId(heldSlotId);

        when(sessionRepository.findByStatus(SessionStatus.ACTIVE)).thenReturn(List.of());
        when(reservationRepository.findHeldSlotIds()).thenReturn(List.of(heldSlotId));
        when(slotRepository.findAll()).thenReturn(List.of(held));

        SlotResyncResultDTO result = service.resync();

        assertEquals(SlotStatus.RESERVED, held.getStatus());
        assertEquals(0, result.correctedSlots());
        verify(slotRepository, never()).save(held);
    }

    /**
     * BR-009-3: an admin edit must not silently break a promise. Without this the slot is freed
     * while the hold stays live — a walk-in takes it, and the booked driver's car lands on a slot
     * that already has a car on it.
     */
    @Test
    void updateStatus_refusesWhenADriverHasBookedTheSlot() {
        UUID id = UUID.randomUUID();
        Slot slot = Slot.builder().slotCode("A05").zone("A").status(SlotStatus.RESERVED).build();
        slot.setId(id);
        when(slotRepository.findById(id)).thenReturn(Optional.of(slot));
        when(reservationRepository.existsBySlotIdAndStatus(id, ReservationStatus.HELD))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> service.updateStatus(
                id, new UpdateSlotStatusRequestDTO(SlotStatus.MAINTENANCE)));
        verify(slotRepository, never()).save(slot);
    }

    @Test
    void deleteSlot_refusesWhenADriverHasBookedTheSlot() {
        UUID id = UUID.randomUUID();
        Slot slot = Slot.builder().slotCode("A05").zone("A").status(SlotStatus.RESERVED).build();
        slot.setId(id);
        when(slotRepository.findById(id)).thenReturn(Optional.of(slot));
        when(reservationRepository.existsBySlotIdAndStatus(id, ReservationStatus.HELD))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> service.deleteSlot(id));
        verify(slotRepository, never()).delete(slot);
    }

    /** Shrinking a zone must not delete a booked slot — it would also break the reservations FK. */
    @Test
    void provisionZone_refusesToRemoveABookedSlot() {
        UUID id = UUID.randomUUID();
        Slot surplus = Slot.builder().slotCode("A03").zone("A").status(SlotStatus.RESERVED).build();
        surplus.setId(id);
        when(slotRepository.findByZoneOrderBySlotCodeAsc("A")).thenReturn(List.of(surplus));
        when(reservationRepository.existsBySlotIdAndStatus(id, ReservationStatus.HELD))
                .thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.provisionZone(new ProvisionZoneRequestDTO("A", 2)));
        verify(slotRepository, never()).deleteAll(anyList());
    }

    /** A hold whose slot flag drifted to EMPTY is repaired back to RESERVED, not left free. */
    @Test
    void resync_repairsHeldSlotThatDriftedToEmpty() {
        UUID heldSlotId = UUID.randomUUID();
        Slot drifted = Slot.builder().slotCode("A05").zone("A").status(SlotStatus.EMPTY).build();
        drifted.setId(heldSlotId);

        when(sessionRepository.findByStatus(SessionStatus.ACTIVE)).thenReturn(List.of());
        when(reservationRepository.findHeldSlotIds()).thenReturn(List.of(heldSlotId));
        when(slotRepository.findAll()).thenReturn(List.of(drifted));

        SlotResyncResultDTO result = service.resync();

        assertEquals(SlotStatus.RESERVED, drifted.getStatus());
        assertEquals(1, result.correctedSlots());
    }
}
