package com.smartparking.billing.exception;

/** A call to the MoMo payment gateway failed (network, bad response, or non-zero create result). */
public class MoMoGatewayException extends RuntimeException {

    public MoMoGatewayException(String message) {
        super(message);
    }

    public MoMoGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
