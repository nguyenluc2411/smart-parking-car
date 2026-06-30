package com.smartparking.billing.controller;

import com.smartparking.billing.dto.momo.MoMoIpnRequestDTO;
import com.smartparking.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BR-005-2 (real-time): MoMo IPN webhook. MoMo's servers POST here when a payment settles, so the
 * invoice is confirmed PAID WITHOUT polling — billing then emits {@code billing.payment.completed}
 * and parking opens the exit gate. Public (no JWT — MoMo can't carry one); authenticity is enforced
 * by the HMAC signature check in {@link BillingService#handleMoMoIpn}. Permitted in SecurityConfig.
 *
 * <p>Note: MoMo can only reach this when billing is publicly reachable (tunnel/ngrok or deployed);
 * on pure localhost the IPN never arrives and the {@code /status} poll remains the fallback.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/billing/momo")
@RequiredArgsConstructor
public class MoMoIpnController {

    private final BillingService billingService;

    @PostMapping("/ipn")
    public ResponseEntity<Void> ipn(@RequestBody MoMoIpnRequestDTO ipn) {
        log.info("MoMo IPN received: orderId={}, resultCode={}", ipn.orderId(), ipn.resultCode());
        billingService.handleMoMoIpn(ipn);
        return ResponseEntity.noContent().build();   // MoMo expects 204
    }
}
