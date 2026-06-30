package com.smartparking.billing.service;

import com.smartparking.billing.dto.request.UpdateRateRequestDTO;
import com.smartparking.billing.dto.response.RateResponseDTO;
import java.util.UUID;

/** Pricing rate management ({@code GET/PUT /api/v1/billing/rates}). */
public interface RateService {

    /** The currently effective rate + its schedules. */
    RateResponseDTO getCurrentRate();

    /** Supersede the current rate with a new effective version (ADMIN). Schedules are carried over. */
    RateResponseDTO updateRate(UpdateRateRequestDTO request, UUID operatorId);
}
