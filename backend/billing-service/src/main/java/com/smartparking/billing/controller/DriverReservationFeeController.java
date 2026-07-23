package com.smartparking.billing.controller;

import com.smartparking.billing.dto.request.CreateReservationFeeRequestDTO;
import com.smartparking.billing.dto.response.ApiResponse;
import com.smartparking.billing.dto.response.ReservationFeeResponseDTO;
import com.smartparking.billing.security.DriverPrincipal;
import com.smartparking.billing.service.ReservationFeeService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Booking fee for a driver reservation (BR-009-11; DRIVER role — enforced by SecurityConfig).
 * {@code reservationId} is the parking-service reservation id, passed by the client — billing-
 * service has no direct read of parking_db (Database Per Service). Controller only (CLAUDE.md §6.4).
 */
@RestController
@RequestMapping("/api/v1/driver/reservations/{reservationId}/fee")
@RequiredArgsConstructor
public class DriverReservationFeeController {

    private final ReservationFeeService reservationFeeService;

    @PostMapping
    public ApiResponse<ReservationFeeResponseDTO> create(
            @AuthenticationPrincipal DriverPrincipal driver,
            @PathVariable UUID reservationId,
            @Valid @RequestBody CreateReservationFeeRequestDTO request) {
        return ApiResponse.ok(reservationFeeService.create(driver, reservationId, request));
    }

    @GetMapping
    public ApiResponse<ReservationFeeResponseDTO> get(
            @AuthenticationPrincipal DriverPrincipal driver, @PathVariable UUID reservationId) {
        return ApiResponse.ok(reservationFeeService.get(driver, reservationId));
    }

    @PostMapping("/refund")
    public ApiResponse<ReservationFeeResponseDTO> refund(
            @AuthenticationPrincipal DriverPrincipal driver, @PathVariable UUID reservationId) {
        return ApiResponse.ok(reservationFeeService.refundOrForfeit(driver, reservationId));
    }
}
