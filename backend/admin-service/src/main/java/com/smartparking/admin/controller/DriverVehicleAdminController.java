package com.smartparking.admin.controller;

import com.smartparking.admin.dto.request.VerifyVehicleRequestDTO;
import com.smartparking.admin.dto.response.ApiResponse;
import com.smartparking.admin.dto.response.DriverVehicleAdminResponseDTO;
import com.smartparking.admin.dto.response.PageResponseDTO;
import com.smartparking.admin.service.DriverVehicleService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator/admin review of driver plate claims (OPERATOR, ADMIN — enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/driver-vehicles")
@RequiredArgsConstructor
public class DriverVehicleAdminController {

    private final DriverVehicleService driverVehicleService;

    @GetMapping
    public ApiResponse<PageResponseDTO<DriverVehicleAdminResponseDTO>> list(
            @RequestParam(defaultValue = "false") boolean verified,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(driverVehicleService.listForVerification(verified, page, size));
    }

    @PostMapping("/{id}/verify")
    public ApiResponse<DriverVehicleAdminResponseDTO> verify(
            @PathVariable UUID id,
            @Valid @RequestBody VerifyVehicleRequestDTO request,
            @AuthenticationPrincipal String actorId) {
        return ApiResponse.ok(
                driverVehicleService.verify(id, request.approved(), UUID.fromString(actorId)));
    }
}
