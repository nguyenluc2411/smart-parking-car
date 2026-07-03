package com.smartparking.billing.controller;

import com.smartparking.billing.service.BillingService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PayOS webhook — public (no JWT); authenticity via PayOS checksum in {@link BillingService#handlePayOsWebhook}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/billing/payos")
@RequiredArgsConstructor
public class PayOsWebhookController {

    private final BillingService billingService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> payload) {
        log.info("PayOS webhook received");
        return ResponseEntity.ok(billingService.handlePayOsWebhook(payload));
    }
}
