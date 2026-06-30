package com.smartparking.parking.service.impl;

import com.smartparking.parking.dto.request.CreateBlacklistRequestDTO;
import com.smartparking.parking.dto.request.CreateWhitelistRequestDTO;
import com.smartparking.parking.dto.response.VehicleResponseDTO;
import com.smartparking.parking.entity.Vehicle;
import com.smartparking.parking.entity.enums.VehicleType;
import com.smartparking.parking.exception.ConflictException;
import com.smartparking.parking.exception.ResourceNotFoundException;
import com.smartparking.parking.repository.VehicleRepository;
import com.smartparking.parking.service.VehicleService;
import com.smartparking.parking.util.PlateNumbers;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponseDTO> listWhitelist() {
        return list(VehicleType.WHITELIST);
    }

    @Override
    @Transactional
    public VehicleResponseDTO addToWhitelist(CreateWhitelistRequestDTO request) {
        return upsert(VehicleType.WHITELIST, request.plateNumber(), request.ownerName(),
                request.note(), request.force());
    }

    @Override
    @Transactional
    public void removeFromWhitelist(String plateNumber) {
        remove(VehicleType.WHITELIST, plateNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponseDTO> listBlacklist() {
        return list(VehicleType.BLACKLIST);
    }

    @Override
    @Transactional
    public VehicleResponseDTO addToBlacklist(CreateBlacklistRequestDTO request) {
        return upsert(VehicleType.BLACKLIST, request.plateNumber(), request.ownerName(),
                request.note(), request.force());
    }

    @Override
    @Transactional
    public void removeFromBlacklist(String plateNumber) {
        remove(VehicleType.BLACKLIST, plateNumber);
    }

    private List<VehicleResponseDTO> list(VehicleType type) {
        return vehicleRepository.findByVehicleTypeOrderByCreatedAtDesc(type).stream()
                .map(v -> toResponse(v, null))
                .toList();
    }

    /**
     * A plate has at most one row (unique). Adding the SAME type is an idempotent update. Adding the
     * OTHER type (whitelist<->blacklist) is a re-classification: rejected with 409 unless {@code force}
     * confirms it — so a plate can never silently flip between the priority and the banned list.
     */
    private VehicleResponseDTO upsert(VehicleType type, String rawPlate, String ownerName, String note,
                                      boolean force) {
        String plate = PlateNumbers.normalize(rawPlate);
        if (!PlateNumbers.isValid(plate)) {
            throw new IllegalArgumentException(
                    "Biển số không hợp lệ: '" + rawPlate + "'. Ví dụ: 51F-12345");
        }
        Optional<Vehicle> existing = vehicleRepository.findByPlateNumber(plate);
        VehicleType from = existing.map(Vehicle::getVehicleType).orElse(null);
        boolean reclassifying = from != null && from != type;
        if (reclassifying && !force) {
            // Prefix lets the dashboard read the current list and ask "chuyển từ {from} sang {type}?".
            throw new ConflictException("RECLASSIFY:" + from + ":Biển " + plate
                    + " đang ở " + from + " — xác nhận để chuyển sang " + type);
        }

        Vehicle vehicle = existing.orElseGet(() -> Vehicle.builder().plateNumber(plate).build());
        vehicle.setVehicleType(type);
        vehicle.setOwnerName(ownerName);
        vehicle.setNote(note);
        vehicle = vehicleRepository.save(vehicle);

        if (reclassifying) {
            log.warn("RECLASSIFY plate {} from {} to {} (owner={})", plate, from, type, ownerName);
        } else {
            log.info("Classified plate {} as {} (owner={})", plate, type, ownerName);
        }
        return toResponse(vehicle, reclassifying ? from : null);
    }

    private void remove(VehicleType type, String plateNumber) {
        String plate = PlateNumbers.normalize(plateNumber);
        if (vehicleRepository.deleteByPlateNumberAndVehicleType(plate, type) == 0) {
            throw new ResourceNotFoundException("Vehicle", plateNumber);
        }
        log.info("Removed plate {} from {}", plate, type);
    }

    private VehicleResponseDTO toResponse(Vehicle vehicle, VehicleType reclassifiedFrom) {
        return new VehicleResponseDTO(vehicle.getId(), vehicle.getPlateNumber(),
                vehicle.getVehicleType(), vehicle.getOwnerName(), vehicle.getNote(),
                vehicle.getCreatedAt(), reclassifiedFrom);
    }
}
