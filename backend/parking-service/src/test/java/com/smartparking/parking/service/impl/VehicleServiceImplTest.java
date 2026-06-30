package com.smartparking.parking.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartparking.parking.dto.request.CreateBlacklistRequestDTO;
import com.smartparking.parking.dto.request.CreateWhitelistRequestDTO;
import com.smartparking.parking.dto.response.VehicleResponseDTO;
import com.smartparking.parking.entity.Vehicle;
import com.smartparking.parking.entity.enums.VehicleType;
import com.smartparking.parking.exception.ConflictException;
import com.smartparking.parking.repository.VehicleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock private VehicleRepository vehicleRepository;
    @InjectMocks private VehicleServiceImpl service;

    private void stubSaveEchoesBack() {
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(UUID.randomUUID());
            }
            return v;
        });
    }

    @Test
    void addToWhitelist_newPlate_createsWhitelistVehicle() {
        when(vehicleRepository.findByPlateNumber("51F-12345")).thenReturn(Optional.empty());
        stubSaveEchoesBack();

        VehicleResponseDTO dto = service.addToWhitelist(
                new CreateWhitelistRequestDTO(" 51f-12345 ", "Nguyen Van A", "VIP", false));

        assertEquals("51F-12345", dto.plateNumber());   // normalized
        assertEquals(VehicleType.WHITELIST, dto.vehicleType());
        assertNull(dto.reclassifiedFrom());             // fresh insert, not a move
    }

    @Test
    void addToWhitelist_invalidPlate_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addToWhitelist(new CreateWhitelistRequestDTO("not-a-plate", null, null, false)));

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void addToWhitelist_samePlateSameType_updatesIdempotently() {
        Vehicle existing = Vehicle.builder().id(UUID.randomUUID()).plateNumber("51F-12345")
                .vehicleType(VehicleType.WHITELIST).build();
        when(vehicleRepository.findByPlateNumber("51F-12345")).thenReturn(Optional.of(existing));
        stubSaveEchoesBack();

        VehicleResponseDTO dto = service.addToWhitelist(
                new CreateWhitelistRequestDTO("51F-12345", "New Owner", "updated", false));

        assertEquals(VehicleType.WHITELIST, dto.vehicleType());
        assertNull(dto.reclassifiedFrom());             // same type -> not a re-classification
    }

    @Test
    void addToBlacklist_plateInWhitelistWithoutForce_throwsConflict() {
        Vehicle existing = Vehicle.builder().id(UUID.randomUUID()).plateNumber("51F-12345")
                .vehicleType(VehicleType.WHITELIST).build();
        when(vehicleRepository.findByPlateNumber("51F-12345")).thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.addToBlacklist(new CreateBlacklistRequestDTO("51F-12345", null, "banned", false)));

        // Prefix + current type let the dashboard build the confirm prompt.
        assertEquals(true, ex.getMessage().startsWith("RECLASSIFY:WHITELIST:"));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void addToBlacklist_plateInWhitelistWithForce_reclassifies() {
        Vehicle existing = Vehicle.builder().id(UUID.randomUUID()).plateNumber("51F-12345")
                .vehicleType(VehicleType.WHITELIST).build();
        when(vehicleRepository.findByPlateNumber("51F-12345")).thenReturn(Optional.of(existing));
        stubSaveEchoesBack();

        VehicleResponseDTO dto = service.addToBlacklist(
                new CreateBlacklistRequestDTO("51F-12345", null, "banned", true));

        assertEquals(VehicleType.BLACKLIST, dto.vehicleType());
        assertEquals(VehicleType.WHITELIST, dto.reclassifiedFrom());   // moved from whitelist
    }
}
