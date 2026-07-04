package com.smartparking.notification.service;

import com.smartparking.notification.dto.AlertEvent;

/**
 * Contract for sending critical parking alerts via Email (SMTP).
 *
 * <p>Implementations must be safe to call even when mail credentials are not configured —
 * in that case they should silently skip sending and log a warning.
 */
public interface EmailNotificationService {

    /**
     * Sends a critical alert email to the configured admin address.
     *
     * @param event the alert event with severity {@code CRITICAL}
     */
    void sendCriticalAlert(AlertEvent event);
}
