package com.smartparking.parking.controller;

import com.smartparking.parking.dto.request.CreateBlacklistRequestDTO;
import com.smartparking.parking.dto.request.CreateWhitelistRequestDTO;
import com.smartparking.parking.dto.response.ApiResponse;
import com.smartparking.parking.dto.response.VehicleResponseDTO;
import com.smartparking.parking.service.VehicleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Whitelist + blacklist management (list = OPERATOR/ADMIN; add/delete = ADMIN via SecurityConfig). */
@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping("/whitelist")
    public ApiResponse<List<VehicleResponseDTO>> listWhitelist() {
        return ApiResponse.ok(vehicleService.listWhitelist());
    }

    @PostMapping("/whitelist")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VehicleResponseDTO> addToWhitelist(@Valid @RequestBody CreateWhitelistRequestDTO request) {
        return ApiResponse.ok(vehicleService.addToWhitelist(request));
    }

    @DeleteMapping("/whitelist/{plate}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWhitelist(@PathVariable String plate) {
        vehicleService.removeFromWhitelist(plate);
    }

    @GetMapping("/blacklist")
    public ApiResponse<List<VehicleResponseDTO>> listBlacklist() {
        return ApiResponse.ok(vehicleService.listBlacklist());
    }

    @PostMapping("/blacklist")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VehicleResponseDTO> addToBlacklist(@Valid @RequestBody CreateBlacklistRequestDTO request) {
        return ApiResponse.ok(vehicleService.addToBlacklist(request));
    }

    @DeleteMapping("/blacklist/{plate}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBlacklist(@PathVariable String plate) {
        vehicleService.removeFromBlacklist(plate);
    }
}
