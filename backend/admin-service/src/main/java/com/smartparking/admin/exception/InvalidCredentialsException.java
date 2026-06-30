package com.smartparking.admin.exception;

/**
 * Thrown when login fails (unknown user, inactive user, or wrong password). Maps to HTTP 401.
 * Deliberately vague to avoid leaking which factor failed.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
