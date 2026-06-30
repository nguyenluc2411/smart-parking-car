package com.smartparking.parking.controller;

import com.smartparking.parking.dto.response.AlertResponseDTO;
import com.smartparking.parking.dto.response.ApiResponse;
import com.smartparking.parking.dto.response.PageResponseDTO;
import com.smartparking.parking.entity.enums.AlertStatus;
import com.smartparking.parking.service.AlertService;
import com.smartparking.parking.sse.AlertSseBroker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Business/security alerts (BR-007) for OPERATOR/ADMIN. Controller only — no business logic. */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final AlertSseBroker sseBroker;

    @GetMapping
    public ApiResponse<PageResponseDTO<AlertResponseDTO>> list(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(alertService.list(status, page, size));
    }

    @PostMapping("/{id}/ack")
    public ApiResponse<AlertResponseDTO> acknowledge(
            @PathVariable UUID id, @AuthenticationPrincipal String operatorId) {
        return ApiResponse.ok(alertService.acknowledge(id, UUID.fromString(operatorId)));
    }

    /** Live alert stream (Server-Sent Events). Clients send the JWT via the Authorization header. */
    @GetMapping("/stream")
    public SseEmitter stream() {
        return sseBroker.register();
    }
}
