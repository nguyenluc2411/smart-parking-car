package com.smartparking.billing.controller;

import com.smartparking.billing.dto.request.DriverPayRequestDTO;
import com.smartparking.billing.dto.response.ApiResponse;
import com.smartparking.billing.dto.response.DriverPaymentResponseDTO;
import com.smartparking.billing.dto.response.InvoiceResponseDTO;
import com.smartparking.billing.dto.response.PageResponseDTO;
import com.smartparking.billing.entity.enums.InvoiceStatus;
import com.smartparking.billing.security.DriverPrincipal;
import com.smartparking.billing.service.BillingService;
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
 * Driver "my invoices" + online self-payment (DRIVER role — enforced by SecurityConfig). Data is
 * scoped to the verified plates in the caller's JWT (ADR-010). Controller only (CLAUDE.md §6.4).
 */
@RestController
@RequestMapping("/api/v1/driver/invoices")
@RequiredArgsConstructor
public class DriverBillingController {

    private final BillingService billingService;

    @GetMapping
    public ApiResponse<PageResponseDTO<InvoiceResponseDTO>> list(
            @AuthenticationPrincipal DriverPrincipal driver,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(billingService.listInvoicesForDriver(driver.plates(), status, page, size));
    }

    @GetMapping("/{invoiceId}")
    public ApiResponse<InvoiceResponseDTO> get(
            @AuthenticationPrincipal DriverPrincipal driver, @PathVariable UUID invoiceId) {
        return ApiResponse.ok(billingService.getInvoiceForDriver(invoiceId, driver.plates()));
    }

    @PostMapping("/{invoiceId}/pay")
    public ApiResponse<DriverPaymentResponseDTO> pay(
            @AuthenticationPrincipal DriverPrincipal driver,
            @PathVariable UUID invoiceId,
            @RequestBody(required = false) DriverPayRequestDTO request) {
        // Body is optional; driver self-pay is always ONLINE (request.method is informational).
        return ApiResponse.ok(
                billingService.payByDriver(invoiceId, driver.driverId(), driver.plates()));
    }
}
