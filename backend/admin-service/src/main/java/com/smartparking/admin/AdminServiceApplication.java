package com.smartparking.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for admin-service (port 8083, admin_db :5433).
 *
 * <p>Consumes every business topic (and all Dead Letter Topics) to build the audit trail.
 * It is a terminal consumer — it does not produce events.
 */
@SpringBootApplication
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
