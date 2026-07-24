package com.smartparking.parking.repository;

import com.smartparking.parking.entity.ParkingZone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingZoneRepository extends JpaRepository<ParkingZone, UUID> {
    List<ParkingZone> findByFloorIdOrderByZoneCodeAsc(UUID floorId);
    Optional<ParkingZone> findByFloorIdAndZoneCode(UUID floorId, String zoneCode);
}
