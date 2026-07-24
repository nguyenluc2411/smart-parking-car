package com.smartparking.parking.repository;

import com.smartparking.parking.entity.ParkingFloor;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingFloorRepository extends JpaRepository<ParkingFloor, UUID> {
    List<ParkingFloor> findByParkingLotIdOrderBySortOrderAsc(UUID lotId);
}
