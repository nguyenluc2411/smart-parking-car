package com.smartparking.admin.service;

/**
 * Delivers an OTP to a phone. Contract only — the real SMS-gateway integration is out of scope for
 * the RBL phase (ADR-010); the default implementation logs the code.
 */
public interface OtpSender {

    void send(String phone, String code);
}
