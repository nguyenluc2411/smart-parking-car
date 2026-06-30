package com.smartparking.admin.service.impl;

import com.smartparking.admin.dto.response.DriverMeResponseDTO;
import com.smartparking.admin.dto.response.DriverVehicleAdminResponseDTO;
import com.smartparking.admin.dto.response.DriverVehicleResponseDTO;
import com.smartparking.admin.dto.response.PageResponseDTO;
import com.smartparking.admin.entity.Driver;
import com.smartparking.admin.entity.DriverVehicle;
import com.smartparking.admin.exception.NotFoundException;
import com.smartparking.admin.repository.DriverRepository;
import com.smartparking.admin.repository.DriverVehicleRepository;
import com.smartparking.admin.service.AuditService;
import com.smartparking.admin.service.DriverVehicleService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver profile/plate management + the operator/admin verification side (ADR-010). Approving a
 * plate makes it eligible for the driver's {@code plates} JWT claim on the next token issuance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverVehicleServiceImpl implements DriverVehicleService {

    private final DriverRepository driverRepository;
    private final DriverVehicleRepository driverVehicleRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public DriverMeResponseDTO getMe(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found: " + driverId));
        List<DriverVehicleResponseDTO> vehicles = driverVehicleRepository
                .findByDriverIdOrderByCreatedAtDesc(driverId).stream()
                .map(v -> new DriverVehicleResponseDTO(v.getId(), v.getPlateNumber(), v.isVerified(),
                        v.getCreatedAt()))
                .toList();
        return new DriverMeResponseDTO(driver.getId(), driver.getPhone(), driver.getFullName(), vehicles);
    }

    @Override
    @Transactional
    public DriverVehicleResponseDTO addVehicle(UUID driverId, String plateNumber) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found: " + driverId));
        // Uniqueness (driver_id, plate_number) is enforced by the DB -> 409 via the handler.
        DriverVehicle saved = driverVehicleRepository.save(DriverVehicle.builder()
                .driver(driver)
                .plateNumber(normalizePlate(plateNumber))
                .verified(false)
                .build());
        log.info("Driver {} claimed plate {} (pending verification)", driverId, saved.getPlateNumber());
        return new DriverVehicleResponseDTO(saved.getId(), saved.getPlateNumber(), saved.isVerified(),
                saved.getCreatedAt());
    }

    @Override
    @Transactional
    public void removeVehicle(UUID driverId, String plateNumber) {
        DriverVehicle vehicle = driverVehicleRepository
                .findByDriverIdAndPlateNumber(driverId, normalizePlate(plateNumber))
                .orElseThrow(() -> new NotFoundException("Plate not found for this driver: " + plateNumber));
        driverVehicleRepository.delete(vehicle);
        log.info("Driver {} removed plate {}", driverId, plateNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<DriverVehicleAdminResponseDTO> listForVerification(boolean verified, int page,
                                                                              int size) {
        Page<DriverVehicle> result = driverVehicleRepository
                .findByVerifiedOrderByCreatedAtDesc(verified, PageRequest.of(page, size));
        List<DriverVehicleAdminResponseDTO> content = result.getContent().stream()
                .map(this::toAdminResponse).toList();
        return PageResponseDTO.of(result, content);
    }

    @Override
    @Transactional
    public DriverVehicleAdminResponseDTO verify(UUID driverVehicleId, boolean approved, UUID actorId) {
        DriverVehicle vehicle = driverVehicleRepository.findById(driverVehicleId)
                .orElseThrow(() -> new NotFoundException("Driver-vehicle not found: " + driverVehicleId));

        if (approved) {
            vehicle.setVerified(true);
            vehicle.setVerifiedBy(actorId);
            vehicle.setVerifiedAt(OffsetDateTime.now());
            driverVehicleRepository.save(vehicle);
            auditService.recordUserAction(actorId, "DRIVER_VEHICLE_VERIFIED", "DriverVehicle",
                    driverVehicleId.toString(), null);
            log.info("Driver-vehicle {} approved by {}", driverVehicleId, actorId);
            return toAdminResponse(vehicle);
        }

        // Rejected: capture the response before removing the row.
        DriverVehicleAdminResponseDTO response = toAdminResponse(vehicle);
        driverVehicleRepository.delete(vehicle);
        auditService.recordUserAction(actorId, "DRIVER_VEHICLE_REJECTED", "DriverVehicle",
                driverVehicleId.toString(), null);
        log.info("Driver-vehicle {} rejected by {}", driverVehicleId, actorId);
        return response;
    }

    /** BR-001-4 parity: uppercase + strip whitespace so claim plates match parking/billing data. */
    private static String normalizePlate(String raw) {
        return raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private DriverVehicleAdminResponseDTO toAdminResponse(DriverVehicle v) {
        Driver d = v.getDriver();
        return new DriverVehicleAdminResponseDTO(v.getId(), d.getId(), d.getPhone(), d.getFullName(),
                v.getPlateNumber(), v.isVerified(), v.getCreatedAt());
    }
}
