package com.smartparking.billing.controller;

import com.smartparking.billing.service.BillingService;
import com.smartparking.billing.service.ReservationFeeService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PayOS webhook — public (no JWT); authenticity via PayOS checksum. Shared across the two things
 * PayOS pays for here: parking invoices and reservation booking fees (BR-009-11) — both draw
 * orderCodes from the same merchant account, so exactly one of the two lookups matches.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/billing/payos")
@RequiredArgsConstructor
public class PayOsWebhookController {

    private final BillingService billingService;
    private final ReservationFeeService reservationFeeService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody Map<String, Object> payload) {
        log.info("PayOS webhook received");
        Map<String, Object> reservationFeeResult = reservationFeeService.handlePayOsWebhookIfKnown(payload);
        if (reservationFeeResult != null) {
            return ResponseEntity.ok(reservationFeeResult);
        }
        return ResponseEntity.ok(billingService.handlePayOsWebhook(payload));
    }
}
