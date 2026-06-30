package com.smartparking.billing.exception;

/**
 * Thrown when no invoice exists for a given session. Maps to HTTP 404 via
 * {@link GlobalExceptionHandler}.
 */
public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(String message) {
        super(message);
    }
}
