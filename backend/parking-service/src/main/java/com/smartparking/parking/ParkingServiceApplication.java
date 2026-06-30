package com.smartparking.parking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for parking-service (port 8081, parking_db :5434).
 *
 * <p>Scheduling is enabled for the transactional outbox poller
 * ({@code com.smartparking.parking.messaging.OutboxPublisher}).
 */
@EnableScheduling
@SpringBootApplication
public class ParkingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkingServiceApplication.class, args);
    }
}
