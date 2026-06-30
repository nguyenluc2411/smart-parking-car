package com.smartparking.parking.controller;

import com.smartparking.parking.dto.response.ApiResponse;
import com.smartparking.parking.dto.response.PageResponseDTO;
import com.smartparking.parking.dto.response.SessionDetailResponseDTO;
import com.smartparking.parking.dto.response.SessionSummaryResponseDTO;
import com.smartparking.parking.entity.enums.SessionStatus;
import com.smartparking.parking.security.DriverPrincipal;
import com.smartparking.parking.service.SessionQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driver "my sessions" (DRIVER role — enforced by SecurityConfig). Data is scoped to the verified
 * plates in the caller's JWT; the client cannot pass a plate (ADR-010). Controller only (CLAUDE.md §6.4).
 */
@RestController
@RequestMapping("/api/v1/driver/sessions")
@RequiredArgsConstructor
public class DriverSessionController {

    private final SessionQueryService sessionQueryService;

    @GetMapping
    public ApiResponse<PageResponseDTO<SessionSummaryResponseDTO>> list(
            @AuthenticationPrincipal DriverPrincipal driver,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(sessionQueryService.searchForDriver(driver.plates(), status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<SessionDetailResponseDTO> get(
            @AuthenticationPrincipal DriverPrincipal driver, @PathVariable UUID id) {
        return ApiResponse.ok(sessionQueryService.getByIdForDriver(id, driver.plates()));
    }
}
