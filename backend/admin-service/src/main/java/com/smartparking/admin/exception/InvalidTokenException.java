package com.smartparking.admin.exception;

/**
 * Thrown when a refresh token is unknown, revoked or expired. Maps to HTTP 401.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
