package com.smartparking.parking.repository;

import com.smartparking.parking.entity.ParkingLot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingLotRepository extends JpaRepository<ParkingLot, UUID> {
}
