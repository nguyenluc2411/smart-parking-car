package com.smartparking.admin.service.impl;

import com.smartparking.admin.service.OtpSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stub OTP delivery: logs the code instead of calling an SMS gateway. Adequate for the RBL demo /
 * mock-driven development; swap for a real gateway in production (ADR-010).
 */
@Slf4j
@Component
public class LoggingOtpSender implements OtpSender {

    @Override
    public void send(String phone, String code) {
        log.info("[OTP] phone={} code={} (stub sender — no real SMS sent)", phone, code);
    }
}
