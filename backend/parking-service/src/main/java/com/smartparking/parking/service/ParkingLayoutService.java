package com.smartparking.parking.service;

import com.smartparking.parking.dto.request.SaveParkingLayoutRequestDTO;
import com.smartparking.parking.dto.request.CreateParkingLotRequestDTO;
import com.smartparking.parking.dto.request.CreateFloorRequestDTO;
import com.smartparking.parking.dto.request.CreateZoneRequestDTO;
import com.smartparking.parking.dto.request.CreateZoneSlotRequestDTO;
import com.smartparking.parking.dto.request.UpdateNameRequestDTO;
import com.smartparking.parking.dto.request.UpdateParkingLotRequestDTO;
import com.smartparking.parking.dto.request.UpdateSlotCodeRequestDTO;
import com.smartparking.parking.dto.response.ParkingLayoutResponseDTO;
import com.smartparking.parking.dto.response.ParkingLotResponseDTO;
import com.smartparking.parking.dto.response.ParkingStructureResponseDTO;
import com.smartparking.parking.dto.response.SlotResponseDTO;
import java.util.List;
import java.util.UUID;

public interface ParkingLayoutService {
    List<ParkingLotResponseDTO> listLots();
    ParkingStructureResponseDTO createLot(CreateParkingLotRequestDTO request);
    ParkingStructureResponseDTO getStructure(UUID lotId);
    ParkingStructureResponseDTO addFloor(UUID lotId, CreateFloorRequestDTO request);
    ParkingStructureResponseDTO addZone(UUID floorId, CreateZoneRequestDTO request);
    ParkingStructureResponseDTO updateLot(UUID lotId, UpdateParkingLotRequestDTO request);
    void deleteLot(UUID lotId);
    ParkingStructureResponseDTO updateFloor(UUID floorId, UpdateNameRequestDTO request);
    void deleteFloor(UUID floorId);
    ParkingStructureResponseDTO updateZone(UUID zoneId, UpdateNameRequestDTO request);
    void deleteZone(UUID zoneId);
    SlotResponseDTO addSlot(UUID zoneId, CreateZoneSlotRequestDTO request);
    SlotResponseDTO updateSlotCode(UUID slotId, UpdateSlotCodeRequestDTO request);
    void deleteSlot(UUID slotId);
    ParkingLayoutResponseDTO getDraft(UUID parkingLotId);
    ParkingLayoutResponseDTO getFloorDraft(UUID floorId);
    ParkingLayoutResponseDTO saveDraft(UUID parkingLotId, SaveParkingLayoutRequestDTO request);
    ParkingLayoutResponseDTO saveFloorDraft(UUID floorId, SaveParkingLayoutRequestDTO request);
    ParkingLayoutResponseDTO publish(UUID parkingLotId);
    ParkingLayoutResponseDTO publishFloor(UUID floorId);
}
