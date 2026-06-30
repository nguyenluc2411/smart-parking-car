package com.smartparking.parking.exception;

/**
 * Thrown when an operation violates a business-state constraint — e.g. an exit detection for a
 * plate that has session history but no ACTIVE session (already closed). Maps to HTTP 409 via
 * {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
