package com.smartparking.notification.service.impl;

import com.smartparking.notification.dto.AlertEvent;
import com.smartparking.notification.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Sends critical parking alerts via SMTP email using Spring's JavaMailSender.
 *
 * <p>If {@code MAIL_USERNAME} or {@code MAIL_ALERT_TO} is blank (the default),
 * the service logs a warning and skips sending — it never throws — so the Kafka
 * consumer loop is never interrupted due to a misconfigured mail integration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationServiceImpl implements EmailNotificationService {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaMailSender mailSender;

    @Value("${app.mail.to:}")
    private String mailTo;

    @Value("${app.mail.from:noreply@parking.vn}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    /**
     * {@inheritDoc}
     *
     * <p>Formats and sends the alert as an email. If credentials are not set,
     * or if the mail API call fails, the error is logged and swallowed.
     */
    @Override
    public void sendCriticalAlert(AlertEvent event) {
        if (!isMailConfigured()) {
            log.warn("[Email] Skipping alert — MAIL_USERNAME or MAIL_ALERT_TO is not configured. " +
                    "alertType={}, plate={}", event.getAlertType(), event.getPlateNumber());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(mailTo);
            message.setSubject(buildSubject(event));
            message.setText(buildBody(event));

            mailSender.send(message);

            log.info("[Email] Critical alert sent to {}. alertType={}, plate={}, gate={}",
                    mailTo, event.getAlertType(), event.getPlateNumber(), event.getGateId());

        } catch (Exception ex) {
            log.error("[Email] Failed to send critical alert email. alertType={}, plate={}, error={}",
                    event.getAlertType(), event.getPlateNumber(), ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isMailConfigured() {
        return mailUsername != null && !mailUsername.isBlank()
                && mailTo != null && !mailTo.isBlank();
    }

    private String buildSubject(AlertEvent event) {
        return String.format("[SMART PARKING] 🚨 CẢNH BÁO KHẨN CẤP: %s — Biển %s",
                event.getAlertType(), event.getPlateNumber());
    }

    private String buildBody(AlertEvent event) {
        String timestamp = event.getCreatedAt() != null
                ? event.getCreatedAt().format(DISPLAY_FORMATTER)
                : "N/A";

        return String.format(
                "🚨 CẢNH BÁO KHẨN CẤP — HỆ THỐNG BÃI GIỮ XE THÔNG MINH%n%n" +
                "Loại cảnh báo : %s%n" +
                "Biển số xe   : %s%n" +
                "Cổng         : %s%n" +
                "Mô tả        : %s%n" +
                "Thời gian    : %s%n%n" +
                "Vui lòng kiểm tra Dashboard tại http://localhost:3000 để xử lý.",
                event.getAlertType(),
                event.getPlateNumber() != null ? event.getPlateNumber() : "N/A",
                event.getGateId() != null ? event.getGateId() : "N/A",
                event.getMessage(),
                timestamp
        );
    }
}
