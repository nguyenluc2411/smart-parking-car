package com.smartparking.parking.repository;

import com.smartparking.parking.entity.ParkingLayout;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingLayoutRepository extends JpaRepository<ParkingLayout, UUID> {
    Optional<ParkingLayout> findByParkingLotId(UUID parkingLotId);
    Optional<ParkingLayout> findByFloorId(UUID floorId);
}
