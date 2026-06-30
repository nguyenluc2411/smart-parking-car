package com.smartparking.admin.service;

import com.smartparking.admin.dto.response.DriverMeResponseDTO;
import com.smartparking.admin.dto.response.DriverVehicleAdminResponseDTO;
import com.smartparking.admin.dto.response.DriverVehicleResponseDTO;
import com.smartparking.admin.dto.response.PageResponseDTO;
import java.util.UUID;

/**
 * Driver profile + plate management, and the operator/admin verification side (ADR-010).
 * Contract only (CLAUDE.md §6.4).
 */
public interface DriverVehicleService {

    DriverMeResponseDTO getMe(UUID driverId);

    DriverVehicleResponseDTO addVehicle(UUID driverId, String plateNumber);

    void removeVehicle(UUID driverId, String plateNumber);

    PageResponseDTO<DriverVehicleAdminResponseDTO> listForVerification(boolean verified, int page, int size);

    DriverVehicleAdminResponseDTO verify(UUID driverVehicleId, boolean approved, UUID actorId);
}
