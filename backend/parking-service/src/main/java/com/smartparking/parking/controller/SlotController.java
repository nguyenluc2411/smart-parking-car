package com.smartparking.parking.controller;

import com.smartparking.parking.dto.request.CreateSlotRequestDTO;
import com.smartparking.parking.dto.request.ProvisionZoneRequestDTO;
import com.smartparking.parking.dto.request.UpdateSlotStatusRequestDTO;
import com.smartparking.parking.dto.response.ApiResponse;
import com.smartparking.parking.dto.response.ProvisionResultDTO;
import com.smartparking.parking.dto.response.SlotAvailabilityResponseDTO;
import com.smartparking.parking.dto.response.SlotResponseDTO;
import com.smartparking.parking.dto.response.SlotResyncResultDTO;
import com.smartparking.parking.service.SlotService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Slot endpoints: reads = OPERATOR/ADMIN; resync + lot setup = ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @GetMapping
    public ApiResponse<List<SlotResponseDTO>> list() {
        return ApiResponse.ok(slotService.listSlots());
    }

    @GetMapping("/availability")
    public ApiResponse<SlotAvailabilityResponseDTO> availability() {
        return ApiResponse.ok(slotService.getAvailability());
    }

    @PostMapping("/resync")
    public ApiResponse<SlotResyncResultDTO> resync() {
        return ApiResponse.ok(slotService.resync());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SlotResponseDTO> create(@Valid @RequestBody CreateSlotRequestDTO request) {
        return ApiResponse.ok(slotService.createSlot(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        slotService.deleteSlot(id);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<SlotResponseDTO> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSlotStatusRequestDTO request) {
        return ApiResponse.ok(slotService.updateStatus(id, request));
    }

    @PostMapping("/provision")
    public ApiResponse<ProvisionResultDTO> provision(
            @Valid @RequestBody ProvisionZoneRequestDTO request) {
        return ApiResponse.ok(slotService.provisionZone(request));
    }
}
