package com.smartparking.parking.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.smartparking.parking.dto.response.SlotAvailabilityResponseDTO;
import com.smartparking.parking.entity.enums.SlotStatus;
import com.smartparking.parking.repository.SessionRepository;
import com.smartparking.parking.repository.SlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlotServiceImplTest {

    @Mock private SlotRepository slotRepository;
    @Mock private SessionRepository sessionRepository;
    @InjectMocks private SlotServiceImpl service;

    @Test
    void getAvailability_computesCountsAndRate() {
        when(slotRepository.count()).thenReturn(50L);
        when(slotRepository.countByStatus(SlotStatus.OCCUPIED)).thenReturn(32L);
        when(slotRepository.countByStatus(SlotStatus.EMPTY)).thenReturn(18L);
        when(slotRepository.countByStatus(SlotStatus.MAINTENANCE)).thenReturn(0L);

        SlotAvailabilityResponseDTO dto = service.getAvailability();

        assertEquals(50L, dto.totalSlots());
        assertEquals(32L, dto.occupiedSlots());
        assertEquals(18L, dto.emptySlots());
        assertEquals(0.64, dto.occupancyRate());
    }

    @Test
    void getAvailability_emptyLot_rateIsZero() {
        when(slotRepository.count()).thenReturn(0L);
        when(slotRepository.countByStatus(SlotStatus.OCCUPIED)).thenReturn(0L);
        when(slotRepository.countByStatus(SlotStatus.EMPTY)).thenReturn(0L);
        when(slotRepository.countByStatus(SlotStatus.MAINTENANCE)).thenReturn(0L);

        assertEquals(0.0, service.getAvailability().occupancyRate());
    }
}
