package com.smartparking.admin.exception;

/** A client error that maps to HTTP 400 (e.g. wrong/expired OTP, missing registration name). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
