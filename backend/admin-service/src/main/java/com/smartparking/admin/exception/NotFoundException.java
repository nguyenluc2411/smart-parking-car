package com.smartparking.admin.exception;

/** Thrown when a referenced resource (user, …) does not exist. Maps to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
