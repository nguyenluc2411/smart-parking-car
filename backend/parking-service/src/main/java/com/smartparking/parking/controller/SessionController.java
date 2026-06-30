package com.smartparking.parking.controller;

import com.smartparking.parking.dto.request.ManualGateRequestDTO;
import com.smartparking.parking.dto.request.SessionResolveRequestDTO;
import com.smartparking.parking.dto.response.ApiResponse;
import com.smartparking.parking.dto.response.PageResponseDTO;
import com.smartparking.parking.dto.response.SessionDetailResponseDTO;
import com.smartparking.parking.dto.response.SessionSummaryResponseDTO;
import com.smartparking.parking.entity.enums.SessionStatus;
import com.smartparking.parking.service.SessionQueryService;
import com.smartparking.parking.service.SessionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Session read + operator actions (OPERATOR/ADMIN). Controller only — no business logic (CLAUDE.md §6.4). */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionQueryService sessionQueryService;
    private final SessionService sessionService;

    @GetMapping
    public ApiResponse<PageResponseDTO<SessionSummaryResponseDTO>> list(
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String plate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(sessionQueryService.search(status, date, plate, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<SessionDetailResponseDTO> get(@PathVariable UUID id) {
        return ApiResponse.ok(sessionQueryService.getById(id));
    }

    /** BR-006-5: operator reconciles a REQUIRES_ATTENTION session (CLOSED or CANCELLED). */
    @PostMapping("/{id}/resolve")
    public ApiResponse<SessionDetailResponseDTO> resolve(
            @PathVariable UUID id, @Valid @RequestBody SessionResolveRequestDTO request) {
        return ApiResponse.ok(sessionQueryService.resolve(id, request.status(), request.note()));
    }

    /** Manual entry — admit a vehicle with a non-standard plate (BR-001-3 format bypassed). */
    @PostMapping("/manual-entry")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionDetailResponseDTO> manualEntry(
            @Valid @RequestBody ManualGateRequestDTO request,
            @AuthenticationPrincipal String operatorId) {
        UUID id = sessionService.manualEntry(request.plateNumber(), request.gateId(),
                request.note(), UUID.fromString(operatorId));
        return ApiResponse.ok(sessionQueryService.getById(id));
    }

    /** Manual exit — release a vehicle by plate (closes ACTIVE session, or flags REQUIRES_ATTENTION). */
    @PostMapping("/manual-exit")
    public ApiResponse<SessionDetailResponseDTO> manualExit(
            @Valid @RequestBody ManualGateRequestDTO request,
            @AuthenticationPrincipal String operatorId) {
        UUID id = sessionService.manualExit(request.plateNumber(), request.gateId(),
                request.note(), UUID.fromString(operatorId));
        return ApiResponse.ok(sessionQueryService.getById(id));
    }
}
