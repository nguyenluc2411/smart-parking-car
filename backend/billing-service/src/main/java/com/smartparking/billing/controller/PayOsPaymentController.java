package com.smartparking.billing.controller;

import com.smartparking.billing.dto.response.ApiResponse;
import com.smartparking.billing.dto.response.PayOsPaymentResponseDTO;
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
 * Gate self-payment via PayOS (parallel to MoMo). OPERATOR/ADMIN JWT required.
 */
@RestController
@RequestMapping("/api/v1/billing/sessions/{sessionId}/payos")
@RequiredArgsConstructor
public class PayOsPaymentController {

    private final BillingService billingService;

    @PostMapping
    public ApiResponse<PayOsPaymentResponseDTO> create(@PathVariable UUID sessionId) {
        return ApiResponse.ok(billingService.createPayOsPayment(sessionId));
    }

    @GetMapping("/status")
    public ApiResponse<PayOsPaymentResponseDTO> status(@PathVariable UUID sessionId,
                                                       @RequestParam long orderCode) {
        return ApiResponse.ok(billingService.checkPayOsPayment(sessionId, orderCode));
    }
}
