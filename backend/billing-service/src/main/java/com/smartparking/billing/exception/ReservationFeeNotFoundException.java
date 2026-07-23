package com.smartparking.billing.exception;

/**
 * Thrown when no reservation fee exists for a given id/reservation. Maps to HTTP 404 via
 * {@link GlobalExceptionHandler}.
 */
public class ReservationFeeNotFoundException extends RuntimeException {

    public ReservationFeeNotFoundException(String message) {
        super(message);
    }
}
