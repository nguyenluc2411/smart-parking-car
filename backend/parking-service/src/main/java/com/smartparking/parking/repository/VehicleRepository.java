package com.smartparking.parking.repository;

import com.smartparking.parking.entity.Vehicle;
import com.smartparking.parking.entity.enums.VehicleType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    /** Lookup for whitelist/blacklist classification (BR-002-1/2). */
    Optional<Vehicle> findByPlateNumber(String plateNumber);

    List<Vehicle> findByVehicleTypeOrderByCreatedAtDesc(VehicleType vehicleType);

    long deleteByPlateNumber(String plateNumber);

    /** Type-scoped delete so a blacklist DELETE cannot accidentally remove a whitelist row. */
    long deleteByPlateNumberAndVehicleType(String plateNumber, VehicleType vehicleType);
}
