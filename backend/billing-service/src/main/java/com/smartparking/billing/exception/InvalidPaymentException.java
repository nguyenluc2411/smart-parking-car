package com.smartparking.billing.exception;

/**
 * Thrown when a payment violates a business-state constraint — invoice not PENDING (already paid /
 * waived) or amount paid below the invoice amount. Maps to HTTP 409 via
 * {@link GlobalExceptionHandler}.
 */
public class InvalidPaymentException extends RuntimeException {

    public InvalidPaymentException(String message) {
        super(message);
    }
}
