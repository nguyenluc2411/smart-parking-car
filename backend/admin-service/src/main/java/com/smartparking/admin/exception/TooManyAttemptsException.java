package com.smartparking.admin.exception;

/** Too many OTP attempts — maps to HTTP 429. */
public class TooManyAttemptsException extends RuntimeException {
    public TooManyAttemptsException(String message) {
        super(message);
    }
}
