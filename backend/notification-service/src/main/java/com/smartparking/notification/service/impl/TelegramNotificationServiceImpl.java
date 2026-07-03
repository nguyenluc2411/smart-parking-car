package com.smartparking.notification.service.impl;

import com.smartparking.notification.dto.AlertEvent;
import com.smartparking.notification.service.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends critical parking alerts to a Telegram chat via the Telegram Bot API.
 *
 * <p>If {@code TELEGRAM_BOT_TOKEN} or {@code TELEGRAM_CHAT_ID} is blank/null (the default),
 * the service logs a warning and skips sending — it never throws — so the Kafka consumer
 * loop is never interrupted due to a misconfigured or absent Telegram integration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationServiceImpl implements TelegramNotificationService {

    private static final String TELEGRAM_API_URL =
            "https://api.telegram.org/bot%s/sendMessage";

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;

    @Value("${app.telegram.bot-token:}")
    private String botToken;

    @Value("${app.telegram.chat-id:}")
    private String chatId;

    /**
     * {@inheritDoc}
     *
     * <p>Formats and sends the alert as a Telegram message. If credentials are not set,
     * or if the Telegram API call fails, the error is logged and swallowed.
     */
    @Override
    public void sendCriticalAlert(AlertEvent event) {
        if (!isTelegramConfigured()) {
            log.warn("[Telegram] Skipping alert — TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is not configured. " +
                    "alertType={}, plate={}", event.getAlertType(), event.getPlateNumber());
            return;
        }

        String text = buildMessage(event);

        try {
            String url = String.format(TELEGRAM_API_URL, botToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, request, String.class);

            log.info("[Telegram] Critical alert sent. alertType={}, plate={}, gate={}",
                    event.getAlertType(), event.getPlateNumber(), event.getGateId());

        } catch (Exception ex) {
            log.error("[Telegram] Failed to send critical alert. alertType={}, plate={}, error={}",
                    event.getAlertType(), event.getPlateNumber(), ex.getMessage(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} only when both token and chat-id are non-blank.
     */
    private boolean isTelegramConfigured() {
        return botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
    }

    /**
     * Builds the human-readable Telegram message text for a critical alert.
     */
    private String buildMessage(AlertEvent event) {
        String timestamp = event.getCreatedAt() != null
                ? event.getCreatedAt().format(DISPLAY_FORMATTER)
                : "N/A";

        return String.format(
                "🚨 [CẢNH BÁO KHẨN CẤP]%n" +
                "Loại: %s%n" +
                "Biển số: %s%n" +
                "Cổng: %s%n" +
                "Mô tả: %s%n" +
                "Thời gian: %s",
                event.getAlertType(),
                event.getPlateNumber(),
                event.getGateId(),
                event.getMessage(),
                timestamp
        );
    }
}
