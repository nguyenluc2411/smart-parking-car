package com.smartparking.notification.service;

import com.smartparking.notification.dto.AlertEvent;

/**
 * Contract for sending critical parking alerts via Telegram.
 *
 * <p>Implementations must be safe to call even when Telegram credentials are not configured —
 * in that case they should silently skip sending and log a warning.
 */
public interface TelegramNotificationService {

    /**
     * Sends a critical alert message to the configured Telegram chat.
     *
     * @param event the alert event with severity {@code CRITICAL}
     */
    void sendCriticalAlert(AlertEvent event);
}
