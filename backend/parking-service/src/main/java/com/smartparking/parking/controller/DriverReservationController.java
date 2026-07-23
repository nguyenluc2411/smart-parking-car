package com.smartparking.parking.controller;

import com.smartparking.parking.dto.request.CreateReservationRequestDTO;
import com.smartparking.parking.dto.response.ApiResponse;
import com.smartparking.parking.dto.response.PageResponseDTO;
import com.smartparking.parking.dto.response.ReservationResponseDTO;
import com.smartparking.parking.security.DriverPrincipal;
import com.smartparking.parking.service.ReservationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driver slot booking (BR-009; DRIVER role — enforced by SecurityConfig). Scoped to the caller's
 * verified plates from the JWT. Controller only (CLAUDE.md §6.4).
 */
@RestController
@RequestMapping("/api/v1/driver/reservations")
@RequiredArgsConstructor
public class DriverReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ApiResponse<ReservationResponseDTO> create(
            @AuthenticationPrincipal DriverPrincipal driver,
            @Valid @RequestBody CreateReservationRequestDTO request) {
        return ApiResponse.ok(reservationService.create(driver, request));
    }

    @GetMapping
    public ApiResponse<PageResponseDTO<ReservationResponseDTO>> list(
            @AuthenticationPrincipal DriverPrincipal driver,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reservationService.listForDriver(driver, page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<ReservationResponseDTO> cancel(
            @AuthenticationPrincipal DriverPrincipal driver, @PathVariable UUID id) {
        return ApiResponse.ok(reservationService.cancel(driver, id));
    }
}
