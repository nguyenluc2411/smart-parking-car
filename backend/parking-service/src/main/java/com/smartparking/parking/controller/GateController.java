package com.smartparking.parking.controller;

import com.smartparking.parking.dto.request.GateOverrideRequestDTO;
import com.smartparking.parking.dto.response.ApiResponse;
import com.smartparking.parking.dto.response.GateResponseDTO;
import com.smartparking.parking.service.GateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gate list (OPERATOR/ADMIN) + manual control (ADMIN — enforced by SecurityConfig). */
@RestController
@RequestMapping("/api/v1/gates")
@RequiredArgsConstructor
public class GateController {

    private final GateService gateService;

    @GetMapping
    public ApiResponse<List<GateResponseDTO>> list() {
        return ApiResponse.ok(gateService.listGates());
    }

    @PostMapping("/{id}/override")
    public ApiResponse<GateResponseDTO> override(
            @PathVariable UUID id,
            @Valid @RequestBody GateOverrideRequestDTO request,
            @AuthenticationPrincipal String operatorId) {
        return ApiResponse.ok(gateService.override(id, request, UUID.fromString(operatorId)));
    }
}
