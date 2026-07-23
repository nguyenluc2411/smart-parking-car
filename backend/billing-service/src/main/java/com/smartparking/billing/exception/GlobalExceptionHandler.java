package com.smartparking.billing.exception;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.kafka.KafkaException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into the standard error envelope from docs/api-contracts.md.
 * Serves the REST endpoints declared for billing-service; the event-driven invoice flow does not
 * surface through here.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRateNotFound(RateNotFoundException ex) {
        log.error("No effective rate", ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "RATE_NOT_CONFIGURED", ex.getMessage(), null);
    }

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleInvoiceNotFound(InvoiceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(ReservationFeeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleReservationFeeNotFound(
            ReservationFeeNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "RESERVATION_FEE_NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidPaymentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPayment(InvalidPaymentException ex) {
        return error(HttpStatus.CONFLICT, "INVALID_PAYMENT", ex.getMessage(), null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        // e.g. unknown payment method enum value (BR-005-1).
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed or invalid request body", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        var fieldError = ex.getBindingResult().getFieldError();
        String field = fieldError != null ? fieldError.getField() : null;
        String message = fieldError != null ? fieldError.getDefaultMessage() : "Validation failed";
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, field);
    }

    @ExceptionHandler(MoMoGatewayException.class)
    public ResponseEntity<Map<String, Object>> handleMoMoGateway(MoMoGatewayException ex) {
        log.error("MoMo gateway failure", ex);
        return error(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", ex.getMessage(), null);
    }

    @ExceptionHandler(PayOsGatewayException.class)
    public ResponseEntity<Map<String, Object>> handlePayOsGateway(PayOsGatewayException ex) {
        log.error("PayOS gateway failure", ex);
        return error(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", ex.getMessage(), null);
    }

    @ExceptionHandler(KafkaException.class)
    public ResponseEntity<Map<String, Object>> handleKafka(KafkaException ex) {
        log.error("Kafka failure", ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "MESSAGING_UNAVAILABLE",
                "Messaging backend unavailable", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", null);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message,
                                                      String field) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        err.put("field", field);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", err);
        body.put("timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
