package com.smartparking.parking.service;

import com.smartparking.parking.dto.request.CreateBlacklistRequestDTO;
import com.smartparking.parking.dto.request.CreateWhitelistRequestDTO;
import com.smartparking.parking.dto.response.VehicleResponseDTO;
import java.util.List;

/** Whitelist (BR-002-2) + blacklist (BR-002-1) management. ADMIN only — enforced by SecurityConfig. */
public interface VehicleService {

    List<VehicleResponseDTO> listWhitelist();

    /** Add or upgrade a vehicle to WHITELIST (idempotent on plate). */
    VehicleResponseDTO addToWhitelist(CreateWhitelistRequestDTO request);

    /** Remove a plate from the whitelist (by normalized plate). */
    void removeFromWhitelist(String plateNumber);

    List<VehicleResponseDTO> listBlacklist();

    /** Add or upgrade a vehicle to BLACKLIST (idempotent on plate). BR-002-1: denies entry. */
    VehicleResponseDTO addToBlacklist(CreateBlacklistRequestDTO request);

    /** Remove a plate from the blacklist (by normalized plate). */
    void removeFromBlacklist(String plateNumber);
}
