package com.smartparking.parking.controller;

import com.smartparking.parking.dto.request.SaveParkingLayoutRequestDTO;
import com.smartparking.parking.dto.request.CreateFloorRequestDTO;
import com.smartparking.parking.dto.request.CreateParkingLotRequestDTO;
import com.smartparking.parking.dto.request.CreateZoneRequestDTO;
import com.smartparking.parking.dto.request.CreateZoneSlotRequestDTO;
import com.smartparking.parking.dto.request.UpdateNameRequestDTO;
import com.smartparking.parking.dto.request.UpdateParkingLotRequestDTO;
import com.smartparking.parking.dto.request.UpdateSlotCodeRequestDTO;
import com.smartparking.parking.dto.response.ApiResponse;
import com.smartparking.parking.dto.response.ParkingLayoutResponseDTO;
import com.smartparking.parking.dto.response.ParkingLotResponseDTO;
import com.smartparking.parking.dto.response.ParkingStructureResponseDTO;
import com.smartparking.parking.dto.response.SlotResponseDTO;
import com.smartparking.parking.service.ParkingLayoutService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parking-lots")
@RequiredArgsConstructor
public class ParkingLayoutController {
    private final ParkingLayoutService layoutService;

    @GetMapping
    public ApiResponse<List<ParkingLotResponseDTO>> listLots() {
        return ApiResponse.ok(layoutService.listLots());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ParkingStructureResponseDTO> createLot(
            @Valid @RequestBody CreateParkingLotRequestDTO request) {
        return ApiResponse.ok(layoutService.createLot(request));
    }

    @GetMapping("/{lotId}/structure")
    public ApiResponse<ParkingStructureResponseDTO> structure(@PathVariable UUID lotId) {
        return ApiResponse.ok(layoutService.getStructure(lotId));
    }

    @PostMapping("/{lotId}/floors")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ParkingStructureResponseDTO> addFloor(
            @PathVariable UUID lotId, @Valid @RequestBody CreateFloorRequestDTO request) {
        return ApiResponse.ok(layoutService.addFloor(lotId, request));
    }

    @PostMapping("/floors/{floorId}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ParkingStructureResponseDTO> addZone(
            @PathVariable UUID floorId, @Valid @RequestBody CreateZoneRequestDTO request) {
        return ApiResponse.ok(layoutService.addZone(floorId, request));
    }

    @PatchMapping("/{lotId}")
    public ApiResponse<ParkingStructureResponseDTO> updateLot(
            @PathVariable UUID lotId,
            @Valid @RequestBody UpdateParkingLotRequestDTO request) {
        return ApiResponse.ok(layoutService.updateLot(lotId, request));
    }

    @DeleteMapping("/{lotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLot(@PathVariable UUID lotId) {
        layoutService.deleteLot(lotId);
    }

    @PatchMapping("/floors/{floorId}")
    public ApiResponse<ParkingStructureResponseDTO> updateFloor(
            @PathVariable UUID floorId,
            @Valid @RequestBody UpdateNameRequestDTO request) {
        return ApiResponse.ok(layoutService.updateFloor(floorId, request));
    }

    @DeleteMapping("/floors/{floorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFloor(@PathVariable UUID floorId) {
        layoutService.deleteFloor(floorId);
    }

    @PatchMapping("/zones/{zoneId}")
    public ApiResponse<ParkingStructureResponseDTO> updateZone(
            @PathVariable UUID zoneId,
            @Valid @RequestBody UpdateNameRequestDTO request) {
        return ApiResponse.ok(layoutService.updateZone(zoneId, request));
    }

    @DeleteMapping("/zones/{zoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteZone(@PathVariable UUID zoneId) {
        layoutService.deleteZone(zoneId);
    }

    @PostMapping("/zones/{zoneId}/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SlotResponseDTO> addSlot(
            @PathVariable UUID zoneId,
            @Valid @RequestBody CreateZoneSlotRequestDTO request) {
        return ApiResponse.ok(layoutService.addSlot(zoneId, request));
    }

    @PatchMapping("/slots/{slotId}")
    public ApiResponse<SlotResponseDTO> updateSlotCode(
            @PathVariable UUID slotId,
            @Valid @RequestBody UpdateSlotCodeRequestDTO request) {
        return ApiResponse.ok(layoutService.updateSlotCode(slotId, request));
    }

    @DeleteMapping("/slots/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(@PathVariable UUID slotId) {
        layoutService.deleteSlot(slotId);
    }

    @GetMapping("/floors/{floorId}/layout")
    public ApiResponse<ParkingLayoutResponseDTO> getFloorLayout(@PathVariable UUID floorId) {
        return ApiResponse.ok(layoutService.getFloorDraft(floorId));
    }

    @PutMapping("/floors/{floorId}/layout")
    public ApiResponse<ParkingLayoutResponseDTO> saveFloorLayout(
            @PathVariable UUID floorId, @Valid @RequestBody SaveParkingLayoutRequestDTO request) {
        return ApiResponse.ok(layoutService.saveFloorDraft(floorId, request));
    }

    @PostMapping("/floors/{floorId}/layout/publish")
    public ApiResponse<ParkingLayoutResponseDTO> publishFloor(@PathVariable UUID floorId) {
        return ApiResponse.ok(layoutService.publishFloor(floorId));
    }

    @GetMapping("/{lotId}/layout")
    public ApiResponse<ParkingLayoutResponseDTO> getLayout(@PathVariable UUID lotId) {
        return ApiResponse.ok(layoutService.getDraft(lotId));
    }

    @PutMapping("/{lotId}/layout")
    public ApiResponse<ParkingLayoutResponseDTO> saveLayout(
            @PathVariable UUID lotId, @Valid @RequestBody SaveParkingLayoutRequestDTO request) {
        return ApiResponse.ok(layoutService.saveDraft(lotId, request));
    }

    @PostMapping("/{lotId}/layout/publish")
    public ApiResponse<ParkingLayoutResponseDTO> publish(@PathVariable UUID lotId) {
        return ApiResponse.ok(layoutService.publish(lotId));
    }
}
