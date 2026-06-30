package com.smartparking.parking.exception;

/** Authenticated but not allowed to access this resource — maps to HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
