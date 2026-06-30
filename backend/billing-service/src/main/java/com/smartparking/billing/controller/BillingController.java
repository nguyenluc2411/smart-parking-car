package com.smartparking.billing.controller;

import com.smartparking.billing.dto.request.PayRequestDTO;
import com.smartparking.billing.dto.response.ApiResponse;
import com.smartparking.billing.dto.response.InvoiceResponseDTO;
import com.smartparking.billing.dto.response.PageResponseDTO;
import com.smartparking.billing.dto.response.PaymentResponseDTO;
import com.smartparking.billing.entity.enums.InvoiceStatus;
import com.smartparking.billing.service.BillingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Billing REST endpoints (docs/api-contracts.md). Controller only — no business logic (CLAUDE.md §6.4).
 * Secured by {@code SecurityConfig} (OPERATOR/ADMIN); the authenticated principal is the user id.
 */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    /** Operator/admin invoice list with optional filters + pagination (newest first). */
    @GetMapping("/invoices")
    public ApiResponse<PageResponseDTO<InvoiceResponseDTO>> listInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) String plate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(billingService.listInvoices(status, plate, date, page, size));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<InvoiceResponseDTO> getInvoice(@PathVariable UUID sessionId) {
        return ApiResponse.ok(billingService.getInvoiceBySession(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/pay")
    public ApiResponse<PaymentResponseDTO> pay(
            @PathVariable UUID sessionId,
            @Valid @RequestBody PayRequestDTO request,
            @AuthenticationPrincipal String operatorId) {
        // operatorId = JWT subject (user id); request is authenticated, so it is always present.
        return ApiResponse.ok(billingService.pay(sessionId, request, UUID.fromString(operatorId)));
    }
}
