package com.smartparking.billing.exception;

/** A call to the PayOS payment gateway failed. */
public class PayOsGatewayException extends RuntimeException {

    public PayOsGatewayException(String message) {
        super(message);
    }

    public PayOsGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
