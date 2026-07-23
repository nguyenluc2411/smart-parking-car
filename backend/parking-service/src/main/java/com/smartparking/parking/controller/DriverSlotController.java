package com.smartparking.parking.controller;

import com.smartparking.parking.dto.response.ApiResponse;
import com.smartparking.parking.dto.response.DriverSlotDTO;
import com.smartparking.parking.service.SlotService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driver-facing slot map (BR-009-10; DRIVER role — enforced by SecurityConfig). Trimmed view of
 * the lot so a driver can pick a spot before booking, without seeing whose plate/session is on
 * any other slot. Controller only (CLAUDE.md §6.4).
 */
@RestController
@RequestMapping("/api/v1/driver/slots")
@RequiredArgsConstructor
public class DriverSlotController {

    private final SlotService slotService;

    @GetMapping
    public ApiResponse<List<DriverSlotDTO>> list() {
        return ApiResponse.ok(slotService.listForDriver());
    }
}
