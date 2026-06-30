package com.smartparking.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for billing-service (port 8082, billing_db :5435).
 *
 * <p>Scheduling is enabled for the transactional outbox poller
 * ({@code com.smartparking.billing.messaging.OutboxPublisher}).
 */
@EnableScheduling
@SpringBootApplication
public class BillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
