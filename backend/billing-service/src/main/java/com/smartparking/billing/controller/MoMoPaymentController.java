package com.smartparking.billing.controller;

import com.smartparking.billing.dto.response.ApiResponse;
import com.smartparking.billing.dto.response.MoMoPaymentResponseDTO;
import com.smartparking.billing.service.BillingService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gate self-payment via MoMo (OPERATOR/ADMIN, secured by {@code /api/v1/billing/**} in
 * SecurityConfig). At the exit the gate terminal creates a MoMo payment for the session's invoice,
 * renders the returned {@code qrCodeUrl}, and polls {@code /status} until MoMo confirms it.
 * Controller only — all logic in {@link BillingService} (CLAUDE.md §6.4).
 */
@RestController
@RequestMapping("/api/v1/billing/sessions/{sessionId}/momo")
@RequiredArgsConstructor
public class MoMoPaymentController {

    private final BillingService billingService;

    /** Create a MoMo payment for the session's PENDING invoice; returns payUrl + qrCodeUrl. */
    @PostMapping
    public ApiResponse<MoMoPaymentResponseDTO> create(@PathVariable UUID sessionId) {
        return ApiResponse.ok(billingService.createMoMoPayment(sessionId));
    }

    /** Query MoMo and reconcile: marks the invoice PAID once the transaction is confirmed. */
    @GetMapping("/status")
    public ApiResponse<MoMoPaymentResponseDTO> status(@PathVariable UUID sessionId,
                                                      @RequestParam String orderId) {
        return ApiResponse.ok(billingService.checkMoMoPayment(sessionId, orderId));
    }
}
