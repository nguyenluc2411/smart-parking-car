package com.smartparking.admin.controller;

import com.smartparking.admin.dto.response.ApiResponse;
import com.smartparking.admin.dto.response.AuditLogResponseDTO;
import com.smartparking.admin.dto.response.PageResponseDTO;
import com.smartparking.admin.service.AuditQueryService;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit trail query (ADMIN — enforced by SecurityConfig). */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    public ApiResponse<PageResponseDTO<AuditLogResponseDTO>> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditQueryService.search(action, userId, from, to, page, size));
    }
}
