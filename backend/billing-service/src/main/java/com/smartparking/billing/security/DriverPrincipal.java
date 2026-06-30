package com.smartparking.billing.security;

import java.util.List;
import java.util.UUID;

/**
 * Security principal for a DRIVER token: the driver id plus the verified plate numbers carried in
 * the JWT {@code plates} claim (ADR-010). Lets driver endpoints scope data to the caller's plates
 * without a cross-service call to admin_db.
 */
public record DriverPrincipal(UUID driverId, List<String> plates) {
}
