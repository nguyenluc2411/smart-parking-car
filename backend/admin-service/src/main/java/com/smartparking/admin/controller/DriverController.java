package com.smartparking.admin.controller;

import com.smartparking.admin.dto.request.AddVehicleRequestDTO;
import com.smartparking.admin.dto.response.ApiResponse;
import com.smartparking.admin.dto.response.DriverMeResponseDTO;
import com.smartparking.admin.dto.response.DriverVehicleResponseDTO;
import com.smartparking.admin.service.DriverVehicleService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driver self-service: profile + plate claims. DRIVER role (enforced by SecurityConfig); the
 * principal is the driver id taken from the JWT subject.
 */
@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
public class DriverController {

    private final DriverVehicleService driverVehicleService;

    @GetMapping("/me")
    public ApiResponse<DriverMeResponseDTO> me(@AuthenticationPrincipal String driverId) {
        return ApiResponse.ok(driverVehicleService.getMe(UUID.fromString(driverId)));
    }

    @PostMapping("/me/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DriverVehicleResponseDTO> addVehicle(
            @AuthenticationPrincipal String driverId,
            @Valid @RequestBody AddVehicleRequestDTO request) {
        return ApiResponse.ok(
                driverVehicleService.addVehicle(UUID.fromString(driverId), request.plateNumber()));
    }

    @DeleteMapping("/me/vehicles/{plate}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeVehicle(@AuthenticationPrincipal String driverId, @PathVariable String plate) {
        driverVehicleService.removeVehicle(UUID.fromString(driverId), plate);
    }
}
