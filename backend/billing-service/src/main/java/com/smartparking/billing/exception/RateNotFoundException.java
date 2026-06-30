package com.smartparking.billing.exception;

/**
 * Thrown when no rate is effective at the time of calculation — a configuration error (billing
 * cannot price a session without an active rate). Surfaces as HTTP 503 / retried to the DLT.
 */
public class RateNotFoundException extends RuntimeException {

    public RateNotFoundException(String message) {
        super(message);
    }
}
