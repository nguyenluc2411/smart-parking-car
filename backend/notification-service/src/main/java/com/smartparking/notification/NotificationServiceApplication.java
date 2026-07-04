package com.smartparking.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification Service — consumes {@code parking.alerts} Kafka topic and
 * dispatches critical alerts to Telegram via Bot API.
 *
 * <p>No database; no Spring Security. Stateless consumer only.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
