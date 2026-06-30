package com.smartparking.admin.repository;

import com.smartparking.admin.entity.DriverVehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverVehicleRepository extends JpaRepository<DriverVehicle, UUID> {

    List<DriverVehicle> findByDriverIdOrderByCreatedAtDesc(UUID driverId);

    Optional<DriverVehicle> findByDriverIdAndPlateNumber(UUID driverId, String plateNumber);

    Page<DriverVehicle> findByVerifiedOrderByCreatedAtDesc(boolean verified, Pageable pageable);

    /** Verified plate numbers of a driver — the source for the JWT {@code plates} claim. */
    @Query("SELECT dv.plateNumber FROM DriverVehicle dv "
            + "WHERE dv.driver.id = :driverId AND dv.verified = true")
    List<String> findVerifiedPlateNumbers(@Param("driverId") UUID driverId);
}
