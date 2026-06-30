package com.smartparking.billing.controller;

import com.smartparking.billing.dto.request.UpdateRateRequestDTO;
import com.smartparking.billing.dto.response.ApiResponse;
import com.smartparking.billing.dto.response.RateResponseDTO;
import com.smartparking.billing.service.RateService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rate endpoints: GET = OPERATOR/ADMIN, PUT = ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/billing/rates")
@RequiredArgsConstructor
public class RateController {

    private final RateService rateService;

    @GetMapping
    public ApiResponse<RateResponseDTO> getCurrent() {
        return ApiResponse.ok(rateService.getCurrentRate());
    }

    @PutMapping
    public ApiResponse<RateResponseDTO> update(
            @Valid @RequestBody UpdateRateRequestDTO request,
            @AuthenticationPrincipal String operatorId) {
        return ApiResponse.ok(rateService.updateRate(request, UUID.fromString(operatorId)));
    }
}
